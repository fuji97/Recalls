package it.federicorapetti.recalls.image

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlin.math.max
import kotlinx.coroutines.runInterruptible

/**
 * Decodes a Ministero della Salute "RICHIAMO" recall PDF by rendering its first page, locating
 * the largest embedded product-photo region with [PdfPageRenderer], and re-rendering a hi-res
 * crop of just that region. Throws when no photo-like region is found so Coil falls back to the
 * app's generic placeholder icon instead of showing a blank or full-page image.
 */
class PdfPhotoDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult = runInterruptible {
        val file = source.file().toFile()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val detection = PdfPageRenderer.detect(page)
                    val region = detection.regions.maxByOrNull { it.width.toLong() * it.height }
                        ?: throw IllegalStateException("No product photo found in PDF")

                    val target = max(
                        options.size.width.pxOrElse { 0 },
                        options.size.height.pxOrElse { 0 }
                    ).takeIf { it > 0 }?.coerceIn(128, 1024) ?: 768

                    DecodeResult(image = detection.crop(region, target).asImage(), isSampled = true)
                }
            }
        }
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (result.mimeType != "application/pdf") return null
            return PdfPhotoDecoder(result.source, options)
        }
    }
}
