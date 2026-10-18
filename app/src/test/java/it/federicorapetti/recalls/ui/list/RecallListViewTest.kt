package it.federicorapetti.recalls.ui.list

import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecallListViewTest {

    private fun entity(
        id: String,
        source: RecallSource = RecallSource.SAFETY_GATE,
        publishedAt: Long = 0L,
        isNew: Boolean = false,
        isRead: Boolean = false,
        title: String = "title",
        brand: String? = null,
        reason: String? = null
    ) = RecallEntity(
        id = id,
        source = source,
        remoteId = id,
        title = title,
        subtitle = null,
        brand = brand,
        reason = reason,
        reference = null,
        country = null,
        publishedAt = publishedAt,
        imageUrl = null,
        webUrl = "https://example.test/$id",
        attachmentUrl = null,
        isRevocation = false,
        isNew = isNew,
        isRead = isRead
    )

    private fun ids(view: RecallListView) = view.groups.flatMap { g -> g.items.map { it.id } }

    @Test
    fun `region filter keeps only that region's sources`() {
        val all = listOf(
            entity("eu", RecallSource.SAFETY_GATE),
            entity("op", RecallSource.IT_OPERATOR),
            entity("mi", RecallSource.IT_MINISTRY)
        )

        assertEquals(listOf("op", "mi"), ids(buildRecallListView(all, RecallListQuery(filter = RegionFilter.ITALY))))
        assertEquals(listOf("eu"), ids(buildRecallListView(all, RecallListQuery(filter = RegionFilter.EU))))
    }

    @Test
    fun `unread-only keeps new unread items only`() {
        val all = listOf(
            entity("unread", isNew = true),
            entity("baseline", isNew = false),
            entity("read", isNew = true, isRead = true)
        )

        val view = buildRecallListView(all, RecallListQuery(unreadOnly = true))

        assertEquals(listOf("unread"), ids(view))
    }

    @Test
    fun `search is trimmed, case-insensitive and covers brand and reason`() {
        val all = listOf(
            entity("brand", brand = "Kettle Co"),
            entity("reason", reason = "may overheat the kettle"),
            entity("other", title = "Toy")
        )

        assertEquals(listOf("brand", "reason"), ids(buildRecallListView(all, RecallListQuery(text = "  KETTLE "))))
        assertEquals(3, ids(buildRecallListView(all, RecallListQuery(text = "   "))).size)
    }

    @Test
    fun `groups by Europe-Rome day and keeps input order within a group`() {
        val late = Instant.parse("2026-03-01T23:30:00Z").toEpochMilli() // 00:30 on 2 March in Rome
        val early = Instant.parse("2026-03-01T10:00:00Z").toEpochMilli()
        val all = listOf(
            entity("z", publishedAt = late),
            entity("a", publishedAt = late + 1),
            entity("old", publishedAt = early)
        )

        val groups = buildRecallListView(all, RecallListQuery()).groups

        assertEquals(listOf(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 1)), groups.map { it.date })
        assertEquals(listOf("z", "a"), groups[0].items.map { it.id })
    }

    @Test
    fun `unread counts ignore the current query`() {
        val all = listOf(
            entity("eu1", RecallSource.SAFETY_GATE, isNew = true),
            entity("eu2", RecallSource.SAFETY_GATE, isNew = true, isRead = true),
            entity("it1", RecallSource.IT_OPERATOR, isNew = true),
            entity("it2", RecallSource.IT_MINISTRY, isNew = true)
        )

        val view = buildRecallListView(
            all,
            RecallListQuery(filter = RegionFilter.EU, text = "zzz", unreadOnly = true)
        )

        assertEquals(
            mapOf(RegionFilter.ALL to 3, RegionFilter.EU to 1, RegionFilter.ITALY to 2),
            view.unreadCounts
        )
    }
}
