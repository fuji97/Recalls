package it.federicorapetti.recalls.data

import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource.IT_MINISTRY
import it.federicorapetti.recalls.data.model.RecallSource.IT_OPERATOR
import it.federicorapetti.recalls.data.model.RecallSource.SAFETY_GATE
import it.federicorapetti.recalls.data.remote.HttpStatusException
import it.federicorapetti.recalls.data.remote.safetygate.SafetyGateApi
import it.federicorapetti.recalls.data.remote.salute.ROME
import it.federicorapetti.recalls.data.remote.salute.SaluteApi
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Collections
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real repository and API clients against canned HTTP responses and in-memory Room. */
@RunWith(RobolectricTestRunner::class)
class RecallRepositoryTest {
    private val dayMs = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    private lateinit var db: RecallDatabase
    private lateinit var store: RecallStore
    private lateinit var barcodeIndex: BarcodeIndex
    private lateinit var repo: RecallRepository

    // Fake server state; defaults are valid empty payloads.
    private var sgNotifications: List<String> = emptyList()
    private var sgSearchStatus = 200
    private val sgDetails = mutableMapOf<Long, Pair<Int, String>>()
    private var operatorNodes: List<String> = emptyList()
    private var operatorEtag: String? = null
    private var rssItems: List<String> = emptyList()
    private val ministryStatus = mutableMapOf<String, Int>()
    private val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())

    private val operatorPath = "/new/page-data/it/avvisi/avvisi-e-richiami-di-prodotti-alimentari/page-data.json"
    private val ministryPrefix = "/new/page-data/it/avvisi-sicurezza-alimentare/"

    private val fake = Interceptor { chain ->
        val request = chain.request()
        requests += request
        val url = request.url
        val path = url.encodedPath
        var mediaType = "application/json"
        var etag: String? = null
        val (code, body) = when {
            path.endsWith("/public/api/search") ->
                sgSearchStatus to """{"content":[${sgNotifications.joinToString(",")}],"totalElements":${sgNotifications.size}}"""

            path.contains("/public/api/notification/") ->
                sgDetails[url.pathSegments.last().toLong()] ?: (404 to "{}")

            path == operatorPath ->
                if (operatorEtag != null && request.header("If-None-Match") == operatorEtag) {
                    304 to ""
                } else {
                    etag = operatorEtag
                    200 to """{"result":{"data":{"extSicurezzaAlimentare":{"nodes":[${operatorNodes.joinToString(",")}]}}}}"""
                }

            path == "/new/rss/RSS_avvisi_sicurezza_alimentare.xml" -> {
                mediaType = "application/xml"
                200 to """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel>${rssItems.joinToString("")}</channel></rss>"""
            }

            path.startsWith(ministryPrefix) -> {
                val slug = url.pathSegments[url.pathSegments.size - 2]
                (ministryStatus[slug] ?: 404) to """{"result":{"data":{"node":{"field_prodotto":"P"}}}}"""
            }

            else -> 404 to "{}"
        }
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("x")
            .apply { etag?.let { header("ETag", it) } }
            .body(body.toResponseBody(mediaType.toMediaType()))
            .build()
    }

    @Before
    fun setUp() {
        db = inMemoryDb()
        store = RecallStore(db)
        val client = OkHttpClient.Builder().addInterceptor(fake).build()
        val sgApi = SafetyGateApi(client)
        barcodeIndex = BarcodeIndex(db, sgApi)
        repo = RecallRepository(store, barcodeIndex, sgApi, SaluteApi(client))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sg(id: Long, publishedAt: Long) =
        """{"id":$id,"publicationDate":"${Instant.ofEpochMilli(publishedAt).atOffset(ZoneOffset.UTC)}","product":{"name":"P$id"}}"""

    private fun itDate(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ROME).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    private fun node(id: String, publishedAt: Long, title: String = "T$id", withdrawn: Boolean = false) =
        """{"id":"$id","dataPubblicazione":"${itDate(publishedAt)}","title":"$title","field_depubblicato":$withdrawn,"path":{"alias":"/x/$id"}}"""

    private fun rss(slug: String, publishedAt: Long) =
        "<item><title>W $slug</title><link>https://www.salute.gov.it/new/it/avvisi-sicurezza-alimentare/$slug</link>" +
            "<description>d</description><pubDate>${itDate(publishedAt)}</pubDate></item>"

    private fun all(): List<RecallEntity> = runBlocking { store.observeAll().first() }

    private fun ids() = all().map { it.id }.toSet()

    private fun ministryFetches() = synchronized(requests) {
        requests.filter { it.url.encodedPath.startsWith(ministryPrefix) }
            .map { it.url.pathSegments[it.url.pathSegments.size - 2] }
    }

    @Test
    fun `baseline sync is silent and a later sync reports new items per source`() = runBlocking {
        sgNotifications = listOf(sg(1, now - dayMs))
        operatorNodes = listOf(node("n1", now - dayMs))
        operatorEtag = "\"v1\""
        rssItems = listOf(rss("a", now - dayMs))
        ministryStatus["a"] = 200

        val first = repo.sync()
        assertTrue(first.newItems.isEmpty())
        assertTrue(first.errors.isEmpty())
        assertEquals(setOf("SG:1", "IT:n1", "ITW:a"), ids())
        assertFalse(all().any { it.isNew })

        sgNotifications = sgNotifications + sg(2, now - dayMs / 2)
        operatorNodes = operatorNodes + node("n2", now)
        operatorEtag = "\"v2\""
        rssItems = rssItems + rss("b", now)
        ministryStatus["b"] = 200

        val second = repo.sync()
        assertTrue(second.errors.isEmpty())
        assertEquals(
            mapOf(
                SAFETY_GATE to listOf("SG:2"),
                IT_OPERATOR to listOf("IT:n2"),
                IT_MINISTRY to listOf("ITW:b")
            ),
            second.newItems.mapValues { it.value.map(RecallEntity::id) }
        )
        assertTrue(second.newItems.values.flatten().all { it.isNew })
    }

    @Test
    fun `a failing source does not stop the others`() = runBlocking {
        sgSearchStatus = 500
        operatorNodes = listOf(node("n1", now - dayMs))
        rssItems = listOf(rss("a", now - dayMs))
        ministryStatus["a"] = 200

        val result = repo.sync()
        assertEquals(setOf(SAFETY_GATE), result.errors.keys)
        assertTrue(result.errors[SAFETY_GATE] is HttpStatusException)
        assertEquals(setOf("IT:n1", "ITW:a"), ids())
    }

    @Test
    fun `operator 304 keeps rows and the ETag`() = runBlocking {
        operatorNodes = listOf(node("n1", now - dayMs, title = "Old"))
        operatorEtag = "\"v1\""
        repo.sync()

        operatorNodes = listOf(node("n1", now - dayMs, title = "Changed"))
        val result = repo.sync()

        val lastOperatorRequest = synchronized(requests) { requests.last { it.url.encodedPath == operatorPath } }
        assertEquals("\"v1\"", lastOperatorRequest.header("If-None-Match"))
        assertFalse(IT_OPERATOR in result.errors)
        assertEquals("Old", all().single { it.id == "IT:n1" }.title)
        assertEquals("\"v1\"", store.etag(IT_OPERATOR))
    }

    @Test
    fun `withdrawn operator recalls are removed`() = runBlocking {
        operatorNodes = listOf(node("n1", now - dayMs), node("n2", now - dayMs))
        repo.sync()
        assertEquals(setOf("IT:n1", "IT:n2"), ids())

        operatorNodes = listOf(node("n1", now - dayMs), node("n2", now - dayMs, withdrawn = true))
        repo.sync()
        assertEquals(setOf("IT:n1"), ids())
    }

    @Test
    fun `recalls older than the history window are not stored and old rows are kept`() = runBlocking {
        store.store(IT_OPERATOR, listOf(content("IT:old", IT_OPERATOR, now - 200 * dayMs)))
        sgNotifications = listOf(sg(1, now - dayMs), sg(9, now - 120 * dayMs))
        operatorNodes = listOf(node("n1", now - dayMs), node("n9", now - 120 * dayMs))
        rssItems = listOf(rss("a", now - dayMs), rss("z", now - 120 * dayMs))
        ministryStatus["a"] = 200
        ministryStatus["z"] = 200

        repo.sync()

        assertEquals(setOf("SG:1", "IT:n1", "ITW:a", "IT:old"), ids())
        assertFalse("z" in ministryFetches())
    }

    @Test
    fun `stored Ministry warnings are not refetched and a failed fetch is retried next sync`() = runBlocking {
        rssItems = listOf(rss("a", now - dayMs), rss("c", now - dayMs))
        ministryStatus["a"] = 200
        ministryStatus["c"] = 500

        val first = repo.sync()
        assertTrue(first.errors.isEmpty())
        assertEquals(setOf("ITW:a"), ids())

        ministryStatus["c"] = 200
        requests.clear()
        val second = repo.sync()

        assertEquals(listOf("c"), ministryFetches())
        assertEquals(setOf("ITW:a", "ITW:c"), ids())
        assertEquals(listOf("ITW:c"), second.newItems[IT_MINISTRY]!!.map { it.id })
    }

    @Test
    fun `Safety Gate barcodes are searchable after a sync`() = runBlocking {
        sgNotifications = listOf(sg(7, now - dayMs))
        sgDetails[7] = 200 to """{"product":{"barcodes":[{"barcode":"03046227700054"}]}}"""

        repo.sync()

        assertEquals(listOf("SG:7"), barcodeIndex.observeMatches("3046227700054").first().map { it.id })
        assertEquals(listOf("SG:7"), barcodeIndex.observeMatches("0003046227700054").first().map { it.id })
    }

    @Test
    fun `barcode indexing failure is not a Safety Gate error`() = runBlocking {
        sgNotifications = listOf(sg(7, now - dayMs))
        sgDetails[7] = 500 to "{}"

        val result = repo.sync()

        assertTrue(result.errors.isEmpty())
        assertTrue("SG:7" in ids())
        assertEquals(1, barcodeIndex.observeUnindexedCount().first())
    }
}
