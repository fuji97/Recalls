package it.federicorapetti.recalls.data

import android.graphics.Bitmap
import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource.IT_OPERATOR
import it.federicorapetti.recalls.data.remote.HttpStatusException
import it.federicorapetti.recalls.data.remote.salute.OperatorPdf
import it.federicorapetti.recalls.data.remote.salute.OperatorPdfFields
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OperatorPdfArchiveTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dayMs = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()
    private val historyStart = now - 90 * dayMs

    private lateinit var db: RecallDatabase
    private lateinit var store: RecallStore
    private var calls = 0
    private var failure: Exception? = null
    private lateinit var archive: OperatorPdfArchive

    private fun pdf() = OperatorPdf(
        listOf(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)),
        OperatorPdfFields(
            null, null, null, null, null, null, null, null, null,
            reasonDetail = "Listeria", warnings = null
        )
    )

    @Before
    fun setUp() {
        db = inMemoryDb()
        store = RecallStore(db)
        archive = OperatorPdfArchive(db, folder.root) {
            calls++
            failure?.let { throw it }
            pdf()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(id: String): RecallEntity = runBlocking { store.get(listOf(id)).single() }

    private fun seed(vararg contents: it.federicorapetti.recalls.data.local.RecallContent) =
        runBlocking { store.store(IT_OPERATOR, contents.toList()) }

    @Test
    fun `processes only eligible rows and survives re-sync`() = runBlocking {
        val a = content("IT:a", IT_OPERATOR, now - dayMs, attachmentUrl = "https://x/a.pdf")
        seed(
            a,
            content("IT:b", IT_OPERATOR, now - dayMs),
            content("IT:c", IT_OPERATOR, now - 120 * dayMs, attachmentUrl = "https://x/c.pdf")
        )

        archive.catchUp(historyStart)

        assertEquals(1, calls)
        val parsed = row("IT:a")
        assertEquals("Listeria", parsed.pdfFields?.reasonDetail)
        assertEquals(1, parsed.pdfPhotoCount)
        assertNotNull(parsed.pdfParsedAt)
        assertTrue(operatorPhotoFile(folder.root, "a", 0).exists())

        archive.catchUp(historyStart)
        assertEquals(1, calls)

        store.store(IT_OPERATOR, listOf(a))
        val after = row("IT:a")
        assertEquals("Listeria", after.pdfFields?.reasonDetail)
        assertEquals(1, after.pdfPhotoCount)
        assertEquals(parsed.pdfParsedAt, after.pdfParsedAt)
    }

    @Test
    fun `transient failure is retried and a permanent one is not`() = runBlocking {
        seed(content("IT:a", IT_OPERATOR, now - dayMs, attachmentUrl = "https://x/a.pdf"))

        failure = IOException("offline")
        archive.catchUp(historyStart)
        assertNull(row("IT:a").pdfParsedAt)

        failure = HttpStatusException(404)
        archive.catchUp(historyStart)
        val gone = row("IT:a")
        assertNotNull(gone.pdfParsedAt)
        assertEquals(0, gone.pdfPhotoCount)
    }

    @Test
    fun `bot shield response stops further downloads`() = runBlocking {
        seed(*(1..3).map {
            content("IT:$it", IT_OPERATOR, now - it * dayMs, attachmentUrl = "https://x/$it.pdf")
        }.toTypedArray())
        failure = HttpStatusException(403)

        archive.catchUp(historyStart)

        assertEquals(1, calls)
        assertTrue((1..3).all { row("IT:$it").pdfParsedAt == null })
    }

    @Test
    fun `deletePhotos removes the recall's photo directory`() = runBlocking {
        seed(content("IT:a", IT_OPERATOR, now - dayMs, attachmentUrl = "https://x/a.pdf"))
        archive.catchUp(historyStart)
        assertTrue(operatorPhotoFile(folder.root, "a", 0).exists())

        archive.deletePhotos(listOf("a"))

        assertFalse(operatorPhotoFile(folder.root, "a", 0).exists())
    }
}
