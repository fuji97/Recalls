package it.federicorapetti.recalls.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import it.federicorapetti.recalls.data.model.RecallSource

@Entity(
    tableName = "recalls",
    indices = [Index("publishedAt"), Index("source")]
)
data class RecallEntity(
    @PrimaryKey val id: String,
    val source: RecallSource,
    val remoteId: String,
    val title: String,
    val subtitle: String?,
    val brand: String?,
    val reason: String?,
    val reference: String?,
    val country: String?,
    val publishedAt: Long,
    val imageUrl: String?,
    val webUrl: String,
    val attachmentUrl: String?,
    val isRevocation: Boolean,
    @ColumnInfo(defaultValue = "0") val isNew: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isRead: Boolean = false
)

/**
 * Every [RecallEntity] column except [RecallEntity.isNew] / [RecallEntity.isRead].
 * Used as a partial entity for upserts so that sync never overwrites the read/new flags.
 */
data class RecallContent(
    val id: String,
    val source: RecallSource,
    val remoteId: String,
    val title: String,
    val subtitle: String?,
    val brand: String?,
    val reason: String?,
    val reference: String?,
    val country: String?,
    val publishedAt: Long,
    val imageUrl: String?,
    val webUrl: String,
    val attachmentUrl: String?,
    val isRevocation: Boolean
)

@Entity(tableName = "source_state")
data class SourceStateEntity(
    @PrimaryKey val source: RecallSource,
    val baselineDone: Boolean,
    val etag: String?,
    val lastSuccessAt: Long?
)

/**
 * Normalized barcodes of one Safety Gate alert (see [it.federicorapetti.recalls.data.remote.safetygate.normalizeBarcodes]);
 * a row with [codes] = "" means "indexed, no barcodes".
 */
@Entity(tableName = "sg_barcodes")
data class SgBarcodeEntity(
    @PrimaryKey val recallId: String, // RecallEntity.id, e.g. "SG:10118951"
    val codes: String
)
