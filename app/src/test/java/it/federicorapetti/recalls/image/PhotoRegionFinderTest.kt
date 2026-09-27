package it.federicorapetti.recalls.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val WIDTH = 600
private const val HEIGHT = 840
private const val WHITE = 0xFFFFFFFF.toInt()
private const val GREY = 0xFF808080.toInt()
private const val BLACK = 0xFF000000.toInt()

class PhotoRegionFinderTest {

    private fun whitePage(): IntArray = IntArray(WIDTH * HEIGHT) { WHITE }

    /** 1-px dark horizontal lines every 4 px, simulating dense body text with no photo block. */
    private fun addTextLines(pixels: IntArray) {
        var y = 0
        while (y < HEIGHT) {
            val rowStart = y * WIDTH
            for (x in 0 until WIDTH) {
                pixels[rowStart + x] = BLACK
            }
            y += 4
        }
    }

    private fun fillRect(pixels: IntArray, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        for (y in top until bottom) {
            val rowStart = y * WIDTH
            for (x in left until right) {
                pixels[rowStart + x] = color
            }
        }
    }

    @Test
    fun `all white page returns null`() {
        assertNull(PhotoRegionFinder.find(whitePage(), WIDTH, HEIGHT))
    }

    @Test
    fun `sparse text lines with no solid block return null`() {
        val pixels = whitePage()
        addTextLines(pixels)

        assertNull(PhotoRegionFinder.find(pixels, WIDTH, HEIGHT))
    }

    @Test
    fun `solid block among text lines is found exactly`() {
        val pixels = whitePage()
        addTextLines(pixels)
        fillRect(pixels, left = 60, top = 480, right = 240, bottom = 720, color = GREY)

        val rect = PhotoRegionFinder.find(pixels, WIDTH, HEIGHT)

        assertEquals(PixelRect(60, 480, 240, 720), rect)
    }

    @Test
    fun `full page grey fill returns null`() {
        val pixels = IntArray(WIDTH * HEIGHT) { GREY }

        assertNull(PhotoRegionFinder.find(pixels, WIDTH, HEIGHT))
    }

    @Test
    fun `thin strip exceeding max aspect ratio returns null`() {
        val pixels = whitePage()
        fillRect(pixels, left = 30, top = 400, right = 570, bottom = 424, color = GREY)

        assertNull(PhotoRegionFinder.find(pixels, WIDTH, HEIGHT))
    }

    @Test
    fun `larger of two disjoint blocks is returned`() {
        val pixels = whitePage()
        fillRect(pixels, left = 60, top = 60, right = 180, bottom = 180, color = GREY)
        fillRect(pixels, left = 300, top = 300, right = 480, bottom = 480, color = GREY)

        val rect = PhotoRegionFinder.find(pixels, WIDTH, HEIGHT)

        assertEquals(PixelRect(300, 300, 480, 480), rect)
    }

    @Test
    fun `findAll returns no regions on an all white page`() {
        assertEquals(emptyList<PixelRect>(), PhotoRegionFinder.findAll(whitePage(), WIDTH, HEIGHT))
    }

    @Test
    fun `findAll returns every qualifying region in top-to-bottom reading order`() {
        val pixels = whitePage()
        fillRect(pixels, left = 300, top = 300, right = 480, bottom = 480, color = GREY) // lower block, filled first
        fillRect(pixels, left = 60, top = 60, right = 180, bottom = 180, color = GREY) // upper block, filled second

        val regions = PhotoRegionFinder.findAll(pixels, WIDTH, HEIGHT)

        assertEquals(
            listOf(
                PixelRect(60, 60, 180, 180),
                PixelRect(300, 300, 480, 480)
            ),
            regions
        )
    }

    @Test
    fun `find returns the largest of the regions findAll reports`() {
        val pixels = whitePage()
        fillRect(pixels, left = 60, top = 60, right = 180, bottom = 180, color = GREY)
        fillRect(pixels, left = 300, top = 300, right = 480, bottom = 480, color = GREY)

        val all = PhotoRegionFinder.findAll(pixels, WIDTH, HEIGHT)
        val best = PhotoRegionFinder.find(pixels, WIDTH, HEIGHT)

        assertEquals(2, all.size)
        assertEquals(PixelRect(300, 300, 480, 480), best)
    }
}
