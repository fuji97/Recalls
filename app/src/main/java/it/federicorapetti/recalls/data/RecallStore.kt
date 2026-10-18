package it.federicorapetti.recalls.data

import androidx.room.withTransaction
import it.federicorapetti.recalls.data.local.RecallContent
import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.local.SourceStateEntity
import it.federicorapetti.recalls.data.model.RecallSource
import kotlinx.coroutines.flow.Flow

/**
 * Local store of Recalls: owns the Baseline sync / New / Read semantics and the per-source sync state.
 */
class RecallStore(private val db: RecallDatabase) {
    private val dao = db.recallDao()

    /**
     * Upserts [contents] and deletes [removedIds] inside one transaction, preserving the
     * isNew/isRead flags of existing rows. The first successful sync of a source is silent
     * (nothing is marked new); later syncs mark newly-seen ids as new and return their entities.
     * The source's state row is written in the same transaction, with [etag] as its new ETag.
     */
    suspend fun store(
        source: RecallSource,
        contents: List<RecallContent>,
        removedIds: List<String> = emptyList(),
        etag: String? = null
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
                etag = etag,
                lastSuccessAt = System.currentTimeMillis()
            )
        )
        if (baselineDone && newIds.isNotEmpty()) dao.getByIds(newIds) else emptyList()
    }

    /** Records a successful "nothing changed" fetch; does nothing if the source was never stored. */
    suspend fun recordUnchanged(source: RecallSource) {
        dao.getState(source)?.let {
            dao.upsertState(it.copy(lastSuccessAt = System.currentTimeMillis()))
        }
    }

    suspend fun etag(source: RecallSource): String? = dao.getState(source)?.etag

    suspend fun newestPublishedAt(source: RecallSource): Long? = dao.maxPublishedAt(source)

    suspend fun storedIds(ids: Collection<String>): Set<String> = dao.existingIds(ids.toList()).toSet()

    fun observeAll(): Flow<List<RecallEntity>> = dao.observeAll()

    fun observe(id: String): Flow<RecallEntity?> = dao.observeById(id)

    suspend fun markRead(id: String) = dao.markRead(id)

    suspend fun markAllRead() = dao.markAllRead()

    fun observeLastSync(): Flow<Long?> = dao.observeLastSuccess()
}
