package it.federicorapetti.recalls.data.remote.salute

import android.content.Context
import android.graphics.Bitmap
import it.federicorapetti.recalls.data.remote.downloadTo
import it.federicorapetti.recalls.image.PdfPhotoExtractor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

data class OperatorPdf(val photos: List<Bitmap>, val fields: OperatorPdfFields?)

class OperatorPdfLoader(private val context: Context, private val httpClient: OkHttpClient) {

    /**
     * Downloads the PDF once and extracts photos and form fields. Throws when the download fails
     * (the caller decides whether to retry); a corrupt or non-template PDF yields no photos and/or
     * null fields.
     */
    suspend fun load(attachmentUrl: String): OperatorPdf = withContext(Dispatchers.IO) {
        val file = File.createTempFile("recall", ".pdf", context.cacheDir)
        try {
            httpClient.downloadTo(attachmentUrl, file)
            val photos = try {
                PdfPhotoExtractor.extract(file)
            } catch (e: Exception) {
                emptyList()
            }
            val fields = try {
                OperatorPdfFieldsReader.read(context, file)
            } catch (e: Exception) {
                null
            } catch (e: LinkageError) {
                null // BouncyCastle is excluded; a certificate-encrypted PDF would hit a missing class.
            }
            OperatorPdf(photos, fields)
        } finally {
            file.delete()
        }
    }
}
