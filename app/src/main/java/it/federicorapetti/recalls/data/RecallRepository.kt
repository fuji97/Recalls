package it.federicorapetti.recalls.data

import androidx.room.withTransaction
import it.federicorapetti.recalls.data.local.RecallContent
import it.federicorapetti.recalls.data.local.RecallDao
import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.local.SourceStateEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.remote.safetygate.SafetyGateApi
import it.federicorapetti.recalls.data.remote.safetygate.SgDetail
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    suspend fun sync(): SyncResult = mutex.withLock {
        val nowMs = System.currentTimeMillis()
        val historyStart = nowMs - HISTORY_WINDOW_MS
        val newItems = mutableMapOf<RecallSource, List<RecallEntity>>()
        val errors = mutableMapOf<RecallSource, Throwable>()

        runCatching { syncSafetyGate(historyStart, nowMs) }
            .onSuccess { if (it.isNotEmpty()) newItems[RecallSource.SAFETY_GATE] = it }
            .onFailure {
                if (it is CancellationException) throw it
                android.util.Log.e("RecallRepository", "SAFETY_GATE sync failed", it)
                errors[RecallSource.SAFETY_GATE] = it
            }

        runCatching { syncItOperator(historyStart) }
            .onSuccess { if (it.isNotEmpty()) newItems[RecallSource.IT_OPERATOR] = it }
            .onFailure { if (it is CancellationException) throw it else errors[RecallSource.IT_OPERATOR] = it }

        runCatching { syncItMinistry(historyStart) }
            .onSuccess { if (it.isNotEmpty()) newItems[RecallSource.IT_MINISTRY] = it }
            .onFailure { if (it is CancellationException) throw it else errors[RecallSource.IT_MINISTRY] = it }

        SyncResult(newItems, errors)
    }

    private suspend fun syncSafetyGate(historyStart: Long, nowMs: Long): List<RecallEntity> {
        val lang = SafetyGateApi.contentLanguage()
        val currentMax = dao.maxPublishedAt(RecallSource.SAFETY_GATE)
        val stopAt = maxOf(historyStart, (currentMax ?: historyStart) - TWO_DAYS_MS)
        val stopYear = Instant.ofEpochMilli(stopAt).atZone(ROME).year
        val nowYear = Instant.ofEpochMilli(nowMs).atZone(ROME).year
        val years = (stopYear..nowYear).sortedDescending()

        val kept = mutableListOf<RecallContent>()
        var page = 0
        while (page < 60) {
            val sgPage = sgApi.search(years, page, lang)
            if (sgPage.content.isEmpty()) break
            var hitOlder = false
            for (notification in sgPage.content) {
                val content = notification.toContent(lang)
                if (content.publishedAt >= stopAt) {
                    kept += content
                } else {
                    hitOlder = true
                }
            }
            if (hitOlder || sgPage.last) break
            page++
        }
        return store(RecallSource.SAFETY_GATE, kept)
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

        val fetched = mutableListOf<RecallContent>()
        for ((slug, item) in toFetch) {
            try {
                val detail = saluteApi.fetchMinistryDetail(slug)
                fetched += ministryContent(item, slug, detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Skipped this round; retried on the next sync.
            }
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

    companion object {
        private const val HISTORY_WINDOW_MS = 90L * 24 * 60 * 60 * 1000
        private const val TWO_DAYS_MS = 2L * 24 * 60 * 60 * 1000
    }
}
