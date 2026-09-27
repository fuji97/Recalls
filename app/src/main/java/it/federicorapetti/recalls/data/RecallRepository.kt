package it.federicorapetti.recalls.data

import androidx.room.withTransaction
import it.federicorapetti.recalls.data.local.RecallContent
import it.federicorapetti.recalls.data.local.RecallDao
import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.local.SgBarcodeEntity
import it.federicorapetti.recalls.data.local.SourceStateEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.remote.safetygate.SG_MAX_PARALLEL_REQUESTS
import it.federicorapetti.recalls.data.remote.safetygate.SafetyGateApi
import it.federicorapetti.recalls.data.remote.safetygate.SgDetail
import it.federicorapetti.recalls.data.remote.safetygate.barcodeSearchKey
import it.federicorapetti.recalls.data.remote.safetygate.normalizeBarcodes
import it.federicorapetti.recalls.data.remote.safetygate.sgPagePlan
import it.federicorapetti.recalls.data.remote.safetygate.toContent
import it.federicorapetti.recalls.data.remote.salute.MinistryRssItem
import it.federicorapetti.recalls.data.remote.salute.OperatorFetch
import it.federicorapetti.recalls.data.remote.salute.ROME
import it.federicorapetti.recalls.data.remote.salute.SaluteApi
import it.federicorapetti.recalls.data.remote.salute.ministryContent
import it.federicorapetti.recalls.data.remote.salute.ministrySlug
import it.federicorapetti.recalls.data.remote.salute.parseItalianDate
import it.federicorapetti.recalls.data.remote.salute.toContent
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class SyncResult(
    val newItems: Map<RecallSource, List<RecallEntity>>,
    val errors: Map<RecallSource, Throwable>
)

class RecallRepository(
    private val db: RecallDatabase,
    private val dao: RecallDao,
    private val sgApi: SafetyGateApi,
    private val saluteApi: SaluteApi
) {
    private val mutex = Mutex()

    /** Per-source completion fraction in [0, 1] for the running sync; null when idle. */
    private val sourceProgress = MutableStateFlow<Map<RecallSource, Float>?>(null)

    /** Overall completion of the running sync in [0, 1] (sources weighted equally); null when idle. */
    val syncProgress: Flow<Float?> = sourceProgress.map { progress -> progress?.values?.average()?.toFloat() }

    private fun report(source: RecallSource, fraction: Float) {
        sourceProgress.update { current -> current?.plus(source to fraction) }
    }

    suspend fun sync(): SyncResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            sourceProgress.value = RecallSource.entries.associateWith { 0f }
            try {
                val nowMs = System.currentTimeMillis()
                val historyStart = nowMs - HISTORY_WINDOW_MS
                val outcomes = coroutineScope {
                    listOf(
                        async { RecallSource.SAFETY_GATE to attempt(RecallSource.SAFETY_GATE) { syncSafetyGate(historyStart, nowMs) } },
                        async { RecallSource.IT_OPERATOR to attempt(RecallSource.IT_OPERATOR) { syncItOperator(historyStart) } },
                        async { RecallSource.IT_MINISTRY to attempt(RecallSource.IT_MINISTRY) { syncItMinistry(historyStart) } }
                    ).awaitAll()
                }
                val newItems = mutableMapOf<RecallSource, List<RecallEntity>>()
                val errors = mutableMapOf<RecallSource, Throwable>()
                for ((source, outcome) in outcomes) {
                    outcome
                        .onSuccess { if (it.isNotEmpty()) newItems[source] = it }
                        .onFailure { errors[source] = it }
                }
                SyncResult(newItems, errors)
            } finally {
                sourceProgress.value = null
            }
        }
    }

    private suspend fun attempt(
        source: RecallSource,
        block: suspend () -> List<RecallEntity>
    ): Result<List<RecallEntity>> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("RecallRepository", "$source sync failed", e)
            Result.failure(e)
        } finally {
            report(source, 1f)
        }

    private suspend fun syncSafetyGate(historyStart: Long, nowMs: Long): List<RecallEntity> {
        val lang = SafetyGateApi.contentLanguage()
        val currentMax = dao.maxPublishedAt(RecallSource.SAFETY_GATE)
        val stopAt = maxOf(historyStart, (currentMax ?: historyStart) - TWO_DAYS_MS)
        val stopYear = Instant.ofEpochMilli(stopAt).atZone(ROME).year
        val nowYear = Instant.ofEpochMilli(nowMs).atZone(ROME).year
        val years = (stopYear..nowYear).sortedDescending()

        val probe = sgApi.search(years, stopAt, page = 0, pageSize = 1, lang = lang)
        check(probe.content.isEmpty() || probe.totalElements > 0) {
            "Safety Gate search response has no totalElements"
        }
        report(RecallSource.SAFETY_GATE, PROGRESS_STARTED)
        val plan = sgPagePlan(probe.totalElements)
        val permits = Semaphore(SG_MAX_PARALLEL_REQUESTS)
        val pagesDone = AtomicInteger()
        val pages = coroutineScope {
            (0 until plan.pageCount).map { page ->
                async {
                    permits.withPermit { sgApi.search(years, stopAt, page, plan.pageSize, lang) }.also {
                        report(
                            RecallSource.SAFETY_GATE,
                            PROGRESS_STARTED + (PROGRESS_FETCHED - PROGRESS_STARTED) * pagesDone.incrementAndGet() / plan.pageCount
                        )
                    }
                }
            }.awaitAll()
        }
        val kept = pages.asSequence()
            .flatMap { it.content }
            .map { it.toContent(lang) }
            .filter { it.publishedAt >= stopAt }
            .distinctBy { it.id }
            .toList()
        val result = store(RecallSource.SAFETY_GATE, kept)
        try {
            indexBarcodes(historyStart, lang)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("RecallRepository", "Safety Gate barcode indexing failed", e)
        }
        return result
    }

    /**
     * Builds the local Safety Gate barcode index (see [SgBarcodeEntity]) for alerts stored within
     * [historyStart]. Above [SG_WEEKLY_REPORT_THRESHOLD] pending alerts, the official weekly XML
     * reports are used first (cheap, but missing the newest ~2 days and alphanumeric codes); the
     * remainder falls back to per-alert detail requests, capped at [SG_DETAIL_INDEX_LIMIT] per sync.
     * Failures are isolated per report/detail fetch so a partial index is still saved.
     */
    private suspend fun indexBarcodes(historyStart: Long, lang: String) {
        var pending = dao.unindexed(RecallSource.SAFETY_GATE, historyStart)
        if (pending.isEmpty()) return

        var weeklyCount = 0
        if (pending.size > SG_WEEKLY_REPORT_THRESHOLD) {
            val cutoff = pending.minOf { it.publishedAt } - ONE_DAY_MS
            val reports = sgApi.weeklyReportList().filter { ref ->
                val date = runCatching { parseItalianDate(ref.publicationDate) }.getOrNull()
                date != null && date >= cutoff
            }
            val reportPermits = Semaphore(SG_MAX_PARALLEL_REQUESTS)
            val maps = coroutineScope {
                reports.map { ref ->
                    async {
                        try {
                            reportPermits.withPermit { sgApi.weeklyReportBarcodes(ref.url) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("RecallRepository", "Weekly report failed: ${ref.url}", e)
                            emptyMap()
                        }
                    }
                }.awaitAll()
            }
            val byRemoteId = mutableMapOf<String, String>()
            maps.forEach { byRemoteId.putAll(it) }
            val rows = pending.mapNotNull { candidate ->
                byRemoteId[candidate.remoteId]?.let { text ->
                    SgBarcodeEntity(candidate.id, normalizeBarcodes(listOf(text)))
                }
            }
            if (rows.isNotEmpty()) dao.upsertBarcodes(rows)
            weeklyCount = rows.size
            pending = pending.filterNot { it.remoteId in byRemoteId }
        }

        val toFetch = pending.take(SG_DETAIL_INDEX_LIMIT)
        val detailPermits = Semaphore(SG_MAX_PARALLEL_REQUESTS)
        val detailRows = coroutineScope {
            toFetch.map { candidate ->
                async {
                    try {
                        val detail = detailPermits.withPermit { sgApi.detail(candidate.remoteId.toLong(), lang) }
                        SgBarcodeEntity(candidate.id, normalizeBarcodes(detail.product.barcodes.mapNotNull { it.barcode }))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll()
        }.filterNotNull()
        if (detailRows.isNotEmpty()) dao.upsertBarcodes(detailRows)

        val stillPending = pending.size - toFetch.size
        android.util.Log.i(
            "RecallRepository",
            "Barcode index: weekly=$weeklyCount detail=${detailRows.size} stillPending=$stillPending"
        )
    }

    private suspend fun syncItOperator(historyStart: Long): List<RecallEntity> {
        val state = dao.getState(RecallSource.IT_OPERATOR)
        return when (val fetch = saluteApi.fetchOperatorRecalls(state?.etag)) {
            is OperatorFetch.NotModified -> {
                dao.getState(RecallSource.IT_OPERATOR)?.let {
                    dao.upsertState(it.copy(lastSuccessAt = System.currentTimeMillis()))
                }
                emptyList()
            }

            is OperatorFetch.Data -> {
                report(RecallSource.IT_OPERATOR, PROGRESS_FETCHED)
                val contents = fetch.nodes
                    .filterNot { it.depubblicato }
                    .map { it.toContent() }
                    .filter { it.publishedAt >= historyStart }
                val removed = fetch.nodes
                    .filter { it.depubblicato }
                    .map { "IT:${it.id}" }
                val result = store(RecallSource.IT_OPERATOR, contents, removed)
                dao.getState(RecallSource.IT_OPERATOR)?.let {
                    dao.upsertState(it.copy(etag = fetch.etag))
                }
                result
            }
        }
    }

    private suspend fun syncItMinistry(historyStart: Long): List<RecallEntity> {
        val items = saluteApi.fetchMinistryRss()
            .filter { parseItalianDate(it.pubDate) >= historyStart }
        val bySlug: Map<String, MinistryRssItem> = items.associateBy { ministrySlug(it) }
        val existing = dao.existingIds(bySlug.keys.map { "ITW:$it" }).toSet()
        val toFetch = bySlug.filterKeys { "ITW:$it" !in existing }
        report(RecallSource.IT_MINISTRY, PROGRESS_STARTED)

        val fetched = mutableListOf<RecallContent>()
        for ((index, entry) in toFetch.entries.withIndex()) {
            val (slug, item) = entry
            try {
                val detail = saluteApi.fetchMinistryDetail(slug)
                fetched += ministryContent(item, slug, detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Skipped this round; retried on the next sync.
            }
            report(
                RecallSource.IT_MINISTRY,
                PROGRESS_STARTED + (PROGRESS_FETCHED - PROGRESS_STARTED) * (index + 1) / toFetch.size
            )
        }
        return store(RecallSource.IT_MINISTRY, fetched)
    }

    /**
     * Upserts [contents] and deletes [removedIds] inside one transaction, preserving the
     * isNew/isRead flags of existing rows. The first successful sync of a source is silent
     * (nothing is marked new); later syncs mark newly-seen ids as new and return their entities.
     */
    private suspend fun store(
        source: RecallSource,
        contents: List<RecallContent>,
        removedIds: List<String> = emptyList()
    ): List<RecallEntity> = db.withTransaction {
        val state = dao.getState(source)
        val ids = contents.map { it.id }
        val existing = dao.existingIds(ids).toSet()
        dao.upsertContent(contents)
        if (removedIds.isNotEmpty()) dao.deleteByIds(removedIds)
        val newIds = ids.filterNot { it in existing }
        val baselineDone = state?.baselineDone == true
        if (baselineDone && newIds.isNotEmpty()) {
            dao.markNew(newIds)
        }
        dao.upsertState(
            SourceStateEntity(
                source = source,
                baselineDone = true,
                etag = state?.etag,
                lastSuccessAt = System.currentTimeMillis()
            )
        )
        if (baselineDone && newIds.isNotEmpty()) dao.getByIds(newIds) else emptyList()
    }

    fun observeAll(): Flow<List<RecallEntity>> = dao.observeAll()

    fun observe(id: String): Flow<RecallEntity?> = dao.observeById(id)

    suspend fun markRead(id: String) = dao.markRead(id)

    suspend fun markAllRead() = dao.markAllRead()

    fun observeLastSync(): Flow<Long?> = dao.observeLastSuccess()

    suspend fun safetyGateDetail(remoteId: String): SgDetail =
        sgApi.detail(remoteId.toLong(), SafetyGateApi.contentLanguage())

    fun observeByBarcode(scanned: String): Flow<List<RecallEntity>> =
        barcodeSearchKey(scanned)?.let(dao::observeByBarcode) ?: flowOf(emptyList())

    fun observeUnindexedBarcodeCount(): Flow<Int> =
        dao.observeUnindexedCount(RecallSource.SAFETY_GATE, System.currentTimeMillis() - HISTORY_WINDOW_MS)

    companion object {
        private const val HISTORY_WINDOW_MS = 90L * 24 * 60 * 60 * 1000
        private const val TWO_DAYS_MS = 2L * 24 * 60 * 60 * 1000
        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
        private const val PROGRESS_STARTED = 0.1f
        private const val PROGRESS_FETCHED = 0.9f
        private const val SG_WEEKLY_REPORT_THRESHOLD = 40
        private const val SG_DETAIL_INDEX_LIMIT = 150
    }
}
