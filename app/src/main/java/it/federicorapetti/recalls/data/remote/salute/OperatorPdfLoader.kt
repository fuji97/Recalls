package it.federicorapetti.recalls.data.remote.salute

import android.content.Context
import android.graphics.Bitmap
import it.federicorapetti.recalls.data.remote.downloadTo
import it.federicorapetti.recalls.image.PdfPhotoExtractor
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient

sealed interface OperatorPdfPart {
    data class Photos(val photos: List<Bitmap>) : OperatorPdfPart
    data class Fields(val fields: OperatorPdfFields?) : OperatorPdfPart
}

class OperatorPdfLoader(private val context: Context, private val httpClient: OkHttpClient) {

    /**
     * Downloads the recall PDF once and feeds both the photo carousel and the form-field rows
     * from that single copy. Emits exactly one [OperatorPdfPart.Photos] first, then one
     * [OperatorPdfPart.Fields] only if the download succeeded. Photos publish first so the
     * carousel never waits on the PDFBox parse. Every failure degrades gracefully: a download
     * failure or corrupt PDF yields no photos and no fields; a PDF that isn't the known
     * operator-form template yields no fields (and possibly still photos, since photo boxes are
     * template-independent). Never throws except cancellation.
     */
    fun load(attachmentUrl: String): Flow<OperatorPdfPart> = flow {
        val file = File.createTempFile("recall", ".pdf", context.cacheDir)
        try {
            try {
                httpClient.downloadTo(attachmentUrl, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(OperatorPdfPart.Photos(emptyList()))
                return@flow
            }
            emit(
                OperatorPdfPart.Photos(
                    try {
                        PdfPhotoExtractor.extract(file)
                    } catch (e: Exception) {
                        emptyList()
                    }
                )
            )
            emit(
                OperatorPdfPart.Fields(
                    try {
                        OperatorPdfFieldsReader.read(context, file)
                    } catch (e: Exception) {
                        null
                    } catch (e: LinkageError) {
                        null // BouncyCastle is excluded; a certificate-encrypted PDF would hit a missing class.
                    }
                )
            )
        } finally {
            file.delete()
        }
    }.flowOn(Dispatchers.IO)
}
