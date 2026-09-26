package it.federicorapetti.recalls.data.remote.salute

import org.junit.Assert.assertEquals
import org.junit.Test

class MinistryRssParserTest {

    @Test
    fun `parses items and skips ones with an empty link`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("ministry_rss.xml")!!
        val items = MinistryRssParser.parse(stream)

        assertEquals(2, items.size)
        assertEquals("prodotto-x", ministrySlug(items[0]))
        assertEquals("19/11/2025", items[0].pubDate)
        assertEquals("prodotto-w", ministrySlug(items[1]))
    }
}
