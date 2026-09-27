package it.federicorapetti.recalls.image

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Extracts a hi-res crop of every product-photo region found on the first page of an
 * already-downloaded Ministero della Salute recall PDF, with [PdfPageRenderer], for display in
 * the detail screen's carousel. Returns an empty list when the PDF has no photo boxes (e.g. a
 * revocation notice) so the UI can fall back to the generic placeholder icon.
 *
 * Blocking; call on [kotlinx.coroutines.Dispatchers.IO].
 */
object PdfPhotoExtractor {
    private const val TARGET_LONG_SIDE = 768

    fun extract(file: File): List<Bitmap> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val detection = PdfPageRenderer.detect(page)
                    detection.regions.map { region -> detection.crop(region, TARGET_LONG_SIDE) }
                }
            }
        }
}
