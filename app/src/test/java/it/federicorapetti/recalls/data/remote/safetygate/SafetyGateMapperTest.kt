package it.federicorapetti.recalls.data.remote.safetygate

import it.federicorapetti.recalls.data.remote.AppJson
import java.time.Instant
import kotlinx.serialization.json.decodeFromStream
import org.junit.Assert.assertEquals
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
}
