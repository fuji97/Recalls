package it.federicorapetti.recalls.data

import android.graphics.Bitmap
import it.federicorapetti.recalls.data.local.OperatorPdfCandidate
import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.local.RecallPdfUpdate
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.remote.HttpStatusException
import it.federicorapetti.recalls.data.remote.salute.OperatorPdf
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val PDF_MAX_PARALLEL = 3
private const val PHOTO_JPEG_QUALITY = 85

private fun photoDir(filesDir: File, remoteId: String) =
    File(filesDir, "operator_photos/${remoteId.filter { it.isLetterOrDigit() || it == '-' }}")

/** Stored photo [index] of an operator recall; [remoteId] is reduced to [A-Za-z0-9-] because it comes from the network. */
fun operatorPhotoFile(filesDir: File, remoteId: String, index: Int): File =
    File(photoDir(filesDir, remoteId), "$index.jpg")

fun RecallEntity.photoFiles(filesDir: File): List<File> =
    List(pdfPhotoCount) { operatorPhotoFile(filesDir, remoteId, it) }

/** GLOSSARY: Preview image. */
fun RecallEntity.previewImage(filesDir: File): Any? = photoFiles(filesDir).firstOrNull() ?: imageUrl

/**
 * Downloads and parses the PDF of every operator recall in the History window that has not been
 * processed yet, storing its form fields on the recall row and its photos as JPEG files.
 */
class OperatorPdfArchive(
    db: RecallDatabase,
    private val filesDir: File,
    private val load: suspend (String) -> OperatorPdf
) {
    private val dao = db.recallDao()

    /**
     * Processes the pending PDFs, newest first, at most [PDF_MAX_PARALLEL] at a time. Never throws
     * except for cancellation: a transient failure is logged and the recall is retried next call; a
     * permanent 4xx marks it processed; 403/429 (bot shield) stops further downloads for this call.
     */
    suspend fun catchUp(historyStart: Long, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
        val candidates = dao.unparsedPdfs(RecallSource.IT_OPERATOR, historyStart)
        if (candidates.isEmpty()) return
        val permits = Semaphore(PDF_MAX_PARALLEL)
        val blocked = AtomicBoolean(false)
        val done = AtomicInteger()
        coroutineScope {
            candidates.map { candidate ->
                async {
                    permits.withPermit { if (!blocked.get()) process(candidate, blocked) }
                    onProgress(done.incrementAndGet(), candidates.size)
                }
            }.awaitAll()
        }
    }

    private suspend fun process(candidate: OperatorPdfCandidate, blocked: AtomicBoolean) {
        try {
            val pdf = load(candidate.attachmentUrl)
            val dir = photoDir(filesDir, candidate.remoteId)
            dir.deleteRecursively()
            pdf.photos.forEachIndexed { index, bitmap ->
                val file = operatorPhotoFile(filesDir, candidate.remoteId, index)
                file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
                bitmap.recycle()
            }
            // Files first, row last: a crash in between leaves the row unparsed and the next sync redoes it.
            dao.updatePdf(RecallPdfUpdate(candidate.id, pdf.fields, pdf.photos.size, System.currentTimeMillis()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpStatusException) {
            when {
                e.code == 403 || e.code == 429 -> blocked.set(true)
                e.code in 400..499 ->
                    dao.updatePdf(RecallPdfUpdate(candidate.id, null, 0, System.currentTimeMillis()))
                else -> android.util.Log.e("OperatorPdfArchive", "PDF of ${candidate.id} failed", e)
            }
        } catch (e: Exception) {
            android.util.Log.e("OperatorPdfArchive", "PDF of ${candidate.id} failed", e)
        }
    }

    fun deletePhotos(remoteIds: Collection<String>) {
        remoteIds.forEach { photoDir(filesDir, it).deleteRecursively() }
    }
}
