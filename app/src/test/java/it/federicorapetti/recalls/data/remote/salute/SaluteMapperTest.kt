package it.federicorapetti.recalls.data.remote.salute

import it.federicorapetti.recalls.data.remote.AppJson
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.decodeFromStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaluteMapperTest {

    private fun loadNodes(): List<SaluteOperatorNode> {
        val stream = javaClass.classLoader!!.getResourceAsStream("salute_page_data.json")!!
        val page: SalutePageData = AppJson.decodeFromStream(stream)
        return page.result.data.extSicurezzaAlimentare.nodes
    }

    @Test
    fun `decoding skips unrelated top-level arrays and keeps all nodes`() {
        assertEquals(3, loadNodes().size)
    }

    @Test
    fun `parses Italian date into Europe Rome epoch millis`() {
        val expectedMillis = OffsetDateTime.of(2026, 9, 24, 0, 0, 0, 0, ZoneOffset.ofHours(2))
            .toInstant()
            .toEpochMilli()

        assertEquals(expectedMillis, parseItalianDate("24/09/2026"))
    }

    @Test
    fun `builds PDF and web URLs with the new prefix`() {
        val node = loadNodes().first { it.id == "uuid-1" }
        val content = node.toContent()

        assertEquals(
            "https://www.salute.gov.it/new/sites/default/files/external_data/avviso1.pdf",
            content.attachmentUrl
        )
        assertEquals(
            "https://www.salute.gov.it/new/it/ext-avviso-sicurezza-alimentare/filetti-di-alici-olio",
            content.webUrl
        )
    }

    @Test
    fun `isRevocation is true only for the Revoca node`() {
        val nodes = loadNodes()

        assertTrue(nodes.first { it.id == "uuid-3" }.toContent().isRevocation)
        assertFalse(nodes.first { it.id == "uuid-1" }.toContent().isRevocation)
        assertFalse(nodes.first { it.id == "uuid-2" }.toContent().isRevocation)
    }
}
