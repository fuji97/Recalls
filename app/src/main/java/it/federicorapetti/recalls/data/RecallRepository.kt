package it.federicorapetti.recalls.data

import it.federicorapetti.recalls.data.local.RecallContent
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.remote.safetygate.SG_MAX_PARALLEL_REQUESTS
import it.federicorapetti.recalls.data.remote.safetygate.SafetyGateApi
import it.federicorapetti.recalls.data.remote.safetygate.SgDetail
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
    private val store: RecallStore,
    private val barcodeIndex: BarcodeIndex,
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
        val currentMax = store.newestPublishedAt(RecallSource.SAFETY_GATE)
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
        val result = store.store(RecallSource.SAFETY_GATE, kept)
        barcodeIndex.catchUp(historyStart, lang)
        return result
    }

    private suspend fun syncItOperator(historyStart: Long): List<RecallEntity> =
        when (val fetch = saluteApi.fetchOperatorRecalls(store.etag(RecallSource.IT_OPERATOR))) {
            is OperatorFetch.NotModified -> {
                store.recordUnchanged(RecallSource.IT_OPERATOR)
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
                store.store(RecallSource.IT_OPERATOR, contents, removed, etag = fetch.etag)
            }
        }

    private suspend fun syncItMinistry(historyStart: Long): List<RecallEntity> {
        val items = saluteApi.fetchMinistryRss()
            .filter { parseItalianDate(it.pubDate) >= historyStart }
        val bySlug: Map<String, MinistryRssItem> = items.associateBy { ministrySlug(it) }
        val existing = store.storedIds(bySlug.keys.map { "ITW:$it" })
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
        return store.store(RecallSource.IT_MINISTRY, fetched)
    }

    suspend fun safetyGateDetail(remoteId: String): SgDetail =
        sgApi.detail(remoteId.toLong(), SafetyGateApi.contentLanguage())

    companion object {
        private const val TWO_DAYS_MS = 2L * 24 * 60 * 60 * 1000
        private const val PROGRESS_STARTED = 0.1f
        private const val PROGRESS_FETCHED = 0.9f
    }
}
