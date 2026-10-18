package it.federicorapetti.recalls.ui.list

import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.local.isUnread
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.model.Region
import it.federicorapetti.recalls.data.remote.salute.ROME
import java.time.Instant
import java.time.LocalDate

data class RecallDayGroup(val date: LocalDate, val items: List<RecallEntity>)

data class RecallListQuery(
    val filter: RegionFilter = RegionFilter.ALL,
    val text: String = "",
    val unreadOnly: Boolean = false
)

data class RecallListView(
    val groups: List<RecallDayGroup>,
    val unreadCounts: Map<RegionFilter, Int>
)

fun RegionFilter.matches(source: RecallSource): Boolean = when (this) {
    RegionFilter.ALL -> true
    RegionFilter.EU -> source.region == Region.EU
    RegionFilter.ITALY -> source.region == Region.ITALY
}

/**
 * Applies [query] to [all] (input order kept) and groups the result by Europe/Rome publication day.
 * [RecallListView.unreadCounts] ignores the query: it counts Unread items per [RegionFilter].
 */
fun buildRecallListView(all: List<RecallEntity>, query: RecallListQuery): RecallListView {
    val groups = all.asSequence()
        .filter { query.filter.matches(it.source) }
        .filter { !query.unreadOnly || it.isUnread }
        .filter { matchesText(it, query.text) }
        .groupBy { Instant.ofEpochMilli(it.publishedAt).atZone(ROME).toLocalDate() }
        .map { (date, items) -> RecallDayGroup(date, items) }

    val unread = all.filter { it.isUnread }
    val unreadCounts = RegionFilter.entries.associateWith { f -> unread.count { f.matches(it.source) } }

    return RecallListView(groups, unreadCounts)
}

private fun matchesText(entity: RecallEntity, text: String): Boolean {
    if (text.isBlank()) return true
    val q = text.trim()
    return listOfNotNull(entity.title, entity.subtitle, entity.brand, entity.reason)
        .any { it.contains(q, ignoreCase = true) }
}
