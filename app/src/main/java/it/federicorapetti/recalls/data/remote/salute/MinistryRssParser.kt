package it.federicorapetti.recalls.data.remote.salute

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class MinistryRssItem(
    val title: String,
    val link: String,
    val description: String,
    val pubDate: String
)

object MinistryRssParser {

    fun parse(input: InputStream): List<MinistryRssItem> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(input)
        val itemNodes = document.getElementsByTagName("item")
        val items = mutableListOf<MinistryRssItem>()
        for (i in 0 until itemNodes.length) {
            val element = itemNodes.item(i) as? Element ?: continue
            val link = element.textOf("link").trim()
            if (link.isEmpty()) continue
            items += MinistryRssItem(
                title = element.textOf("title").trim(),
                link = link,
                description = element.textOf("description").trim(),
                pubDate = element.textOf("pubDate").trim()
            )
        }
        return items
    }

    private fun Element.textOf(tag: String): String {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0).textContent ?: "" else ""
    }
}
