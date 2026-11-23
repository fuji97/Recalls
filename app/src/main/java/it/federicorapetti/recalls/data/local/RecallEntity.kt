package it.federicorapetti.recalls.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import it.federicorapetti.recalls.data.remote.salute.OperatorPdfFields
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
    @ColumnInfo(defaultValue = "0") val isRead: Boolean = false,
    @Embedded(prefix = "pdf_") val pdfFields: OperatorPdfFields? = null,
    @ColumnInfo(defaultValue = "0") val pdfPhotoCount: Int = 0,
    /** Set once the operator recall's PDF was processed (photos/fields stored, or permanently unavailable). */
    val pdfParsedAt: Long? = null
)

/** GLOSSARY: Unread = New and not yet Read. */
val RecallEntity.isUnread: Boolean get() = isNew && !isRead

/**
 * Every [RecallEntity] column except [RecallEntity.isNew] / [RecallEntity.isRead] and the `pdf*`
 * columns (owned by OperatorPdfArchive).
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

/** PDF-derived columns of one operator recall, written by OperatorPdfArchive. */
data class RecallPdfUpdate(
    val id: String,
    @Embedded(prefix = "pdf_") val pdfFields: OperatorPdfFields?,
    val pdfPhotoCount: Int,
    val pdfParsedAt: Long
)

@Entity(tableName = "source_state")
data class SourceStateEntity(
    @PrimaryKey val source: RecallSource,
    val baselineDone: Boolean,
    val etag: String?,
    val lastSuccessAt: Long?
)

/**
 * Normalized barcodes of one Safety Gate alert (see [it.federicorapetti.recalls.data.BarcodeIndex]);
 * a row with [codes] = "" means "indexed, no barcodes".
 */
@Entity(tableName = "sg_barcodes")
data class SgBarcodeEntity(
    @PrimaryKey val recallId: String, // RecallEntity.id, e.g. "SG:10118951"
    val codes: String
)
