package it.federicorapetti.recalls.image

/** A pixel-space rectangle, right/bottom exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * Finds the bounding box of the most likely product-photo region in a rendered PDF page.
 *
 * The Ministero della Salute "RICHIAMO" form (Allegato 2) embeds product photos as solid
 * raster blocks surrounded by mostly-white form text. This scans the page on a coarse grid,
 * marks grid cells that are mostly non-white, groups adjacent dense cells into connected
 * components, and picks the largest component whose bounding box looks like a photo rather
 * than a logo, a text paragraph, or a full-page scan.
 */
object PhotoRegionFinder {
    private const val CELL = 6
    private const val WHITE_LUM = 230
    private const val DENSE_MIN = 27 // 75% of CELL*CELL (36)
    private const val MIN_AREA_FRAC = 0.015
    private const val MAX_AREA_FRAC = 0.5
    private const val MAX_ASPECT = 4

    /** [pixels] are ARGB_8888 ints, row-major, [width]×[height]. Returns null when no photo-like region qualifies. */
    fun find(pixels: IntArray, width: Int, height: Int): PixelRect? =
        findRegions(pixels, width, height).maxByOrNull { it.cellCount }?.rect

    /**
     * Same detection as [find], but returns every qualifying region instead of only the largest,
     * in top-to-bottom, left-to-right reading order. Used to populate a multi-photo carousel from
     * a single PDF page. Returns an empty list when no photo-like region qualifies.
     */
    fun findAll(pixels: IntArray, width: Int, height: Int): List<PixelRect> =
        findRegions(pixels, width, height).map { it.rect }

    private class Region(val rect: PixelRect, val cellCount: Int)

    private fun findRegions(pixels: IntArray, width: Int, height: Int): List<Region> {
        val gw = width / CELL
        val gh = height / CELL
        if (gw == 0 || gh == 0) return emptyList()

        val dense = BooleanArray(gw * gh)
        for (gy in 0 until gh) {
            for (gx in 0 until gw) {
                var nonWhite = 0
                val baseX = gx * CELL
                val baseY = gy * CELL
                for (dy in 0 until CELL) {
                    val rowStart = (baseY + dy) * width + baseX
                    for (dx in 0 until CELL) {
                        val p = pixels[rowStart + dx]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        val lum = (r * 299 + g * 587 + b * 114) / 1000
                        if (lum < WHITE_LUM) nonWhite++
                    }
                }
                if (nonWhite >= DENSE_MIN) dense[gy * gw + gx] = true
            }
        }

        val visited = BooleanArray(gw * gh)
        val queue = IntArray(gw * gh)
        val regions = mutableListOf<Region>()

        for (start in 0 until gw * gh) {
            if (!dense[start] || visited[start]) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var cellCount = 0
            var x0 = gw
            var y0 = gh
            var x1 = -1
            var y1 = -1

            while (head < tail) {
                val idx = queue[head++]
                val x = idx % gw
                val y = idx / gw
                cellCount++
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y

                if (x > 0) {
                    val n = idx - 1
                    if (dense[n] && !visited[n]) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
                if (x < gw - 1) {
                    val n = idx + 1
                    if (dense[n] && !visited[n]) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
                if (y > 0) {
                    val n = idx - gw
                    if (dense[n] && !visited[n]) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
                if (y < gh - 1) {
                    val n = idx + gw
                    if (dense[n] && !visited[n]) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
            }

            val bw = x1 - x0 + 1
            val bh = y1 - y0 + 1
            val areaFrac = (bw * bh) / (gw * gh).toDouble()
            if (areaFrac !in MIN_AREA_FRAC..MAX_AREA_FRAC) continue
            if (maxOf(bw, bh) > MAX_ASPECT * minOf(bw, bh)) continue

            val rect = PixelRect(x0 * CELL, y0 * CELL, (x1 + 1) * CELL, (y1 + 1) * CELL)
            regions += Region(rect, cellCount)
        }

        return regions
    }
}
