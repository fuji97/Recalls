package it.federicorapetti.recalls.data.remote.safetygate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGateWeeklyReportParserTest {

    @Test
    fun `parses report list and skips entries with an empty URL`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("sg_weekly_report_list.xml")!!
        val refs = SafetyGateWeeklyReportParser.parseList(stream)

        assertEquals(2, refs.size)
        assertEquals("25/09/2026", refs[0].publicationDate)
        assertTrue(refs[0].url.contains("&search="))
    }

    @Test
    fun `parses barcodes keyed by alert id and skips notifications without one`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("sg_weekly_report.xml")!!
        val barcodes = SafetyGateWeeklyReportParser.parseBarcodes(stream)

        assertEquals(3, barcodes.size)
        assertEquals("5908275105350", barcodes["10118911"])
        assertEquals("87185463409328718546348440", barcodes["10118967"])
        assertEquals("", barcodes["10118884"])
    }
}
