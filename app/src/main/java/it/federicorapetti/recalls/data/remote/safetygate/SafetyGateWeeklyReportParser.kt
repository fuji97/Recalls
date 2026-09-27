package it.federicorapetti.recalls.data.remote.safetygate

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** One entry from the weekly report list: [publicationDate] as "dd/MM/yyyy", [url] absolute. */
data class SgWeeklyReportRef(val publicationDate: String, val url: String)

private val ALERT_ID_PATTERN = Regex("""alertDetail/(\d+)""")

object SafetyGateWeeklyReportParser {

    /** `weeklyReport` elements → (publicationDate, URL) with both trimmed; entries with either blank are skipped. */
    fun parseList(input: InputStream): List<SgWeeklyReportRef> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
        val nodes = document.getElementsByTagName("weeklyReport")
        val refs = mutableListOf<SgWeeklyReportRef>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            val publicationDate = element.textOf("publicationDate").trim()
            val url = element.textOf("URL").trim()
            if (publicationDate.isEmpty() || url.isEmpty()) continue
            refs += SgWeeklyReportRef(publicationDate, url)
        }
        return refs
    }

    /**
     * `notifications` elements → alert id (digits captured by [ALERT_ID_PATTERN] on `reference`) to raw
     * `barcode` text ("" when the element is missing or empty); notifications without an id are skipped.
     */
    fun parseBarcodes(input: InputStream): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
        val nodes = document.getElementsByTagName("notifications")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            val reference = element.textOf("reference")
            val id = ALERT_ID_PATTERN.find(reference)?.groupValues?.get(1) ?: continue
            result[id] = element.textOf("barcode")
        }
        return result
    }

    private fun Element.textOf(tag: String): String {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0).textContent ?: "" else ""
    }
}
