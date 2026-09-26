package it.federicorapetti.recalls.data.remote.salute

import it.federicorapetti.recalls.data.local.RecallContent
import it.federicorapetti.recalls.data.model.RecallSource
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val ROME: ZoneId = ZoneId.of("Europe/Rome")
private val ITALIAN_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun parseItalianDate(s: String): Long =
    LocalDate.parse(s, ITALIAN_DATE_FORMAT).atStartOfDay(ROME).toInstant().toEpochMilli()

fun SaluteOperatorNode.toContent(): RecallContent {
    val motivoName = relationships.motivo?.name
    val attachmentUrl = relationships.allegati?.firstOrNull()?.url
        ?.let { "https://www.salute.gov.it/new$it" }
    return RecallContent(
        id = "IT:$id",
        source = RecallSource.IT_OPERATOR,
        remoteId = id,
        title = title.trim(),
        subtitle = null,
        brand = marca?.trim(),
        reason = motivoName,
        reference = null,
        country = null,
        publishedAt = parseItalianDate(dataPubblicazione),
        imageUrl = null,
        webUrl = "https://www.salute.gov.it/new/it${path.alias}",
        attachmentUrl = attachmentUrl,
        isRevocation = motivoName?.startsWith("Revoca") == true
    )
}

fun ministrySlug(item: MinistryRssItem): String =
    item.link.trimEnd('/').substringAfterLast('/')

fun ministryContent(item: MinistryRssItem, slug: String, detail: MinistryPageData): RecallContent {
    val node = detail.result.data.node
    val imageUrl = node.relationships.fieldImages.firstOrNull()?.uri?.url
        ?.let { "https://www.salute.gov.it/new$it" }
    return RecallContent(
        id = "ITW:$slug",
        source = RecallSource.IT_MINISTRY,
        remoteId = slug,
        title = item.title.trim(),
        subtitle = node.fieldProdotto,
        brand = node.fieldMarca,
        reason = node.fieldSostanza,
        reference = null,
        country = node.fieldNazione,
        publishedAt = parseItalianDate(item.pubDate),
        imageUrl = imageUrl,
        webUrl = item.link,
        attachmentUrl = null,
        isRevocation = false
    )
}
