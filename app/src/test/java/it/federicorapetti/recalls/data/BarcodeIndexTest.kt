package it.federicorapetti.recalls.data

import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.model.RecallSource.SAFETY_GATE
import it.federicorapetti.recalls.data.remote.safetygate.SafetyGateApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BarcodeIndexTest {
    private val dayMs = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    private lateinit var db: RecallDatabase
    private lateinit var store: RecallStore
    private lateinit var index: BarcodeIndex

    /** Canned detail responses by alert id; anything else (e.g. a weekly report) answers 404. */
    private val details = mutableMapOf<Long, Pair<Int, String>>()
    private val requestedIds = mutableListOf<Long>()

    private val fake = Interceptor { chain ->
        val request = chain.request()
        val segments = request.url.pathSegments
        val id = if (segments.getOrNull(segments.size - 2) == "notification") segments.last().toLongOrNull() else null
        val (code, body) = id?.also { requestedIds += it }?.let { details[it] } ?: (404 to "{}")
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("x")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Before
    fun setUp() {
        db = inMemoryDb()
        store = RecallStore(db)
        index = BarcodeIndex(db, SafetyGateApi(OkHttpClient.Builder().addInterceptor(fake).build()))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun detail(vararg barcodes: String) =
        200 to """{"product":{"barcodes":[${barcodes.joinToString(",") { """{"barcode":"$it"}""" }}]}}"""

    private suspend fun seed(id: Long, publishedAt: Long = now - dayMs) {
        store.store(SAFETY_GATE, listOf(content("SG:$id", SAFETY_GATE, publishedAt)))
    }

    private suspend fun indexed(id: Long, vararg barcodes: String) {
        seed(id)
        details[id] = detail(*barcodes)
        index.catchUp(now - HISTORY_WINDOW_MS, "en")
    }

    private fun matches(scanned: String) = runBlocking { index.observeMatches(scanned).first().map { it.id } }

    @Test
    fun `zero-padded stored barcode matches scans with or without padding`() = runBlocking {
        indexed(1, "03046227700054")

        assertEquals(listOf("SG:1"), matches("3046227700054"))
        assertEquals(listOf("SG:1"), matches("0003046227700054"))
    }

    @Test
    fun `scan with fewer than 7 significant digits matches nothing`() = runBlocking {
        indexed(1, "03046227700054")

        assertTrue(matches("000012345").isEmpty())
    }

    @Test
    fun `both codes of a concatenated entry match`() = runBlocking {
        indexed(1, "87185463409328718546348440")

        assertEquals(listOf("SG:1"), matches("8718546340932"))
        assertEquals(listOf("SG:1"), matches("8718546348440"))
    }

    @Test
    fun `alphanumeric entry is reduced to digits and short entries are dropped`() = runBlocking {
        indexed(1, "N2525-41621", "12345")

        assertEquals(listOf("SG:1"), matches("252541621"))
        assertTrue(matches("12345").isEmpty())
    }

    @Test
    fun `alert without barcodes counts as indexed and matches nothing`() = runBlocking {
        seed(1)
        details[1] = detail()

        index.catchUp(now - HISTORY_WINDOW_MS, "en")

        assertEquals(0, index.observeUnindexedCount().first())
        assertTrue(matches("3046227700054").isEmpty())
    }

    @Test
    fun `failed detail request does not throw and the alert stays unindexed`() = runBlocking {
        seed(1)
        details[1] = 500 to "{}"

        index.catchUp(now - HISTORY_WINDOW_MS, "en")

        assertEquals(1, index.observeUnindexedCount().first())
    }

    @Test
    fun `alert outside the history window is neither requested nor counted`() = runBlocking {
        seed(1, publishedAt = now - 100 * dayMs)
        details[1] = detail("03046227700054")

        index.catchUp(now - HISTORY_WINDOW_MS, "en")

        assertFalse(1L in requestedIds)
        assertEquals(0, index.observeUnindexedCount().first())
    }
}
