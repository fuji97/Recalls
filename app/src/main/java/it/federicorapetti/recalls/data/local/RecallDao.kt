package it.federicorapetti.recalls.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import it.federicorapetti.recalls.data.model.RecallSource
import kotlinx.coroutines.flow.Flow

@Dao
interface RecallDao {

    @Query("SELECT * FROM recalls ORDER BY publishedAt DESC, id")
    fun observeAll(): Flow<List<RecallEntity>>

    @Query("SELECT * FROM recalls WHERE id = :id")
    fun observeById(id: String): Flow<RecallEntity?>

    @Query("SELECT id FROM recalls WHERE id IN (:ids)")
    suspend fun existingIdsChunk(ids: List<String>): List<String>

    suspend fun existingIds(ids: List<String>): List<String> =
        ids.chunked(500).flatMap { existingIdsChunk(it) }

    @Upsert(entity = RecallEntity::class)
    suspend fun upsertContent(items: List<RecallContent>)

    @Query("UPDATE recalls SET isNew = 1 WHERE id IN (:ids)")
    suspend fun markNew(ids: List<String>)

    @Query("UPDATE recalls SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE recalls SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM recalls WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM recalls WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<RecallEntity>

    @Query("SELECT MAX(publishedAt) FROM recalls WHERE source = :source")
    suspend fun maxPublishedAt(source: RecallSource): Long?

    @Query("SELECT * FROM source_state WHERE source = :source")
    suspend fun getState(source: RecallSource): SourceStateEntity?

    @Upsert
    suspend fun upsertState(state: SourceStateEntity)

    @Query("SELECT MAX(lastSuccessAt) FROM source_state")
    fun observeLastSuccess(): Flow<Long?>

    @Query(
        "SELECT id, remoteId, publishedAt FROM recalls WHERE source = :source AND publishedAt >= :from " +
            "AND id NOT IN (SELECT recallId FROM sg_barcodes) ORDER BY publishedAt DESC"
    )
    suspend fun unindexed(source: RecallSource, from: Long): List<SgIndexCandidate>

    @Query(
        "SELECT COUNT(*) FROM recalls WHERE source = :source AND publishedAt >= :from " +
            "AND id NOT IN (SELECT recallId FROM sg_barcodes)"
    )
    fun observeUnindexedCount(source: RecallSource, from: Long): Flow<Int>

    @Upsert
    suspend fun upsertBarcodes(rows: List<SgBarcodeEntity>)

    @Query(
        "SELECT r.* FROM recalls r INNER JOIN sg_barcodes b ON b.recallId = r.id " +
            "WHERE b.codes LIKE '%' || :key || '%' ORDER BY r.publishedAt DESC, r.id"
    )
    fun observeByBarcode(key: String): Flow<List<RecallEntity>>
}

data class SgIndexCandidate(val id: String, val remoteId: String, val publishedAt: Long)
