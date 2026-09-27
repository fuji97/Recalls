package it.federicorapetti.recalls.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import it.federicorapetti.recalls.data.remote.HttpStatusException
import it.federicorapetti.recalls.data.remote.execute
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a Ministero della Salute recall PDF and extracts a hi-res crop of every product-photo
 * region found on its first page with [PdfPageRenderer], for display in the detail screen's
 * carousel. Returns an empty list when the PDF has no photo boxes (e.g. a revocation notice) so
 * the UI can fall back to the generic placeholder icon.
 */
object PdfPhotoExtractor {
    private const val TARGET_LONG_SIDE = 768

    suspend fun extract(context: Context, httpClient: OkHttpClient, pdfUrl: String): List<Bitmap> =
        withContext(Dispatchers.IO) {
            val file = File.createTempFile("recall_photos", ".pdf", context.cacheDir)
            try {
                download(httpClient, pdfUrl, file)
                renderCrops(file)
            } finally {
                file.delete()
            }
        }

    private suspend fun download(httpClient: OkHttpClient, url: String, file: File) {
        val request = Request.Builder().url(url).build()
        httpClient.execute(request) { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            file.outputStream().use { out -> response.body.byteStream().copyTo(out) }
        }
    }

    private fun renderCrops(file: File): List<Bitmap> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val detection = PdfPageRenderer.detect(page)
                    detection.regions.map { region -> detection.crop(region, TARGET_LONG_SIDE) }
                }
            }
        }
}
