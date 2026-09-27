package it.federicorapetti.recalls.data.remote.safetygate

import it.federicorapetti.recalls.data.remote.AppJson
import java.time.Instant
import kotlinx.serialization.json.decodeFromStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGateMapperTest {

    private fun loadPage(): SgPage {
        val stream = javaClass.classLoader!!.getResourceAsStream("sg_search.json")!!
        return AppJson.decodeFromStream(stream)
    }

    @Test
    fun `maps id title brand and reason`() {
        val page = loadPage()
        val content = page.content.first().toContent("en")

        assertEquals("SG:12345", content.id)
        assertEquals("12345", content.remoteId)
        // title falls back to nameSpecific because product.name is null.
        assertEquals("Maschera respiratoria", content.title)
        assertEquals("AcmeBrand, OtherBrand", content.brand)
        assertEquals("CHEMICAL,INJURIES", content.reason)
    }

    @Test
    fun `maps publishedAt from ISO offset date`() {
        val page = loadPage()
        val content = page.content.first().toContent("en")

        assertEquals(Instant.parse("2026-09-24T13:57:06.082Z").toEpochMilli(), content.publishedAt)
    }

    @Test
    fun `thumbnail uses the mainPicture photo id`() {
        val page = loadPage()
        val content = page.content.first().toContent("en")

        assertEquals(SafetyGateApi.thumbnailUrl(222), content.imageUrl)
    }

    @Test
    fun `normalizeBarcodes strips non-digits and drops short entries`() {
        assertEquals(
            "8433327011145 252541621",
            normalizeBarcodes(listOf("8433327011145 ", "2027-2", "N252541621"))
        )
    }

    @Test
    fun `barcodeSearchKey strips leading zeros`() {
        assertEquals("12345678905", barcodeSearchKey("0012345678905"))
    }

    @Test
    fun `barcodeSearchKey rejects codes shorter than 7 digits`() {
        assertEquals(null, barcodeSearchKey("000012345"))
    }

    @Test
    fun `barcodeSearchKey matches a zero-padded stored barcode as a substring`() {
        val key = barcodeSearchKey("3046227700054")!!
        assertTrue(key in normalizeBarcodes(listOf("03046227700054")))
    }

    @Test
    fun `normalizeBarcodes preserves both codes from a concatenated XML entry`() {
        val codes = normalizeBarcodes(listOf("87185463409328718546348440"))
        assertTrue("8718546340932" in codes)
        assertTrue("8718546348440" in codes)
    }
}
