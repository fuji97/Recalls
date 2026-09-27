package it.federicorapetti.recalls.image

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders a [PdfRenderer.Page] at low resolution to locate product-photo regions with
 * [PhotoRegionFinder], then re-renders hi-res crops of individual regions on demand. Shared by
 * [PdfPhotoDecoder] (single largest-region crop for list thumbnails) and [PdfPhotoExtractor]
 * (every region, for the detail-screen carousel).
 */
object PdfPageRenderer {
    private const val DETECT_WIDTH = 600

    /** The regions found on a page, plus enough context to crop any of them at a target size. */
    class Detection internal constructor(
        val regions: List<PixelRect>,
        private val page: PdfRenderer.Page,
        private val detectPerPagePoint: Float,
    ) {
        /** Renders a hi-res crop of [region] whose longer side is [targetLongSide] pixels. */
        fun crop(region: PixelRect, targetLongSide: Int): Bitmap {
            val scale = targetLongSide.toFloat() / max(region.width, region.height)
            val outW = (region.width * scale).roundToInt()
            val outH = (region.height * scale).roundToInt()
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            out.eraseColor(Color.WHITE)
            val matrix = Matrix().apply {
                setScale(detectPerPagePoint * scale, detectPerPagePoint * scale)
                postTranslate(-region.left * scale, -region.top * scale)
            }
            page.render(out, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return out
        }
    }

    /** Renders [page] at [DETECT_WIDTH] and locates every photo-like region on it. */
    fun detect(page: PdfRenderer.Page): Detection {
        val detectHeight = (DETECT_WIDTH.toFloat() * page.height / page.width).roundToInt()
        val detectBitmap = Bitmap.createBitmap(DETECT_WIDTH, detectHeight, Bitmap.Config.ARGB_8888)
        detectBitmap.eraseColor(Color.WHITE)
        page.render(detectBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        val pixels = IntArray(DETECT_WIDTH * detectHeight)
        detectBitmap.getPixels(pixels, 0, DETECT_WIDTH, 0, 0, DETECT_WIDTH, detectHeight)
        detectBitmap.recycle()

        val regions = PhotoRegionFinder.findAll(pixels, DETECT_WIDTH, detectHeight)
        return Detection(regions, page, DETECT_WIDTH.toFloat() / page.width)
    }
}
