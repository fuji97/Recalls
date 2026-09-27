package it.federicorapetti.recalls.data.remote.safetygate

import it.federicorapetti.recalls.data.local.RecallContent
import it.federicorapetti.recalls.data.model.RecallSource
import java.time.OffsetDateTime

fun SgNotification.toContent(lang: String): RecallContent {
    val title = product.name?.takeIf { it.isNotBlank() }
        ?: product.nameSpecific?.takeIf { it.isNotBlank() }
        ?: reference
        ?: id.toString()
    val subtitle = product.nameSpecific
        ?.takeIf { it.isNotBlank() && it != title }
    val brand = product.brands
        .mapNotNull { it.brand?.takeIf(String::isNotBlank) }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
    val reason = risk?.riskType
        ?.mapNotNull { it.name?.takeIf(String::isNotBlank) }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(",")
    val photo = product.photos.firstOrNull { it.mainPicture } ?: product.photos.firstOrNull()
    val imageUrl = photo?.id?.let { SafetyGateApi.thumbnailUrl(it) }
    val publishedAt = OffsetDateTime.parse(publicationDate).toInstant().toEpochMilli()

    return RecallContent(
        id = "SG:$id",
        source = RecallSource.SAFETY_GATE,
        remoteId = id.toString(),
        title = title,
        subtitle = subtitle,
        brand = brand,
        reason = reason,
        reference = reference,
        country = null,
        publishedAt = publishedAt,
        imageUrl = imageUrl,
        webUrl = SafetyGateApi.webUrl(id, lang),
        attachmentUrl = null,
        isRevocation = false
    )
}

private const val MIN_STORED_BARCODE_DIGITS = 8
private const val MIN_BARCODE_KEY_DIGITS = 7

/** Each raw entry reduced to its digits; entries shorter than 8 digits dropped; joined with a space. "" when none. */
fun normalizeBarcodes(raw: List<String>): String =
    raw.map { entry -> entry.filter(Char::isDigit) }
        .filter { it.length >= MIN_STORED_BARCODE_DIGITS }
        .joinToString(" ")

/** Lookup key for a scanned code: digits only, leading zeros removed; null when fewer than 7 digits remain. */
fun barcodeSearchKey(scanned: String): String? =
    scanned.filter(Char::isDigit).trimStart('0').takeIf { it.length >= MIN_BARCODE_KEY_DIGITS }
