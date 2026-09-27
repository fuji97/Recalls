package it.federicorapetti.recalls.data.remote.salute

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Reads the structured fields of a Ministero della Salute "RICHIAMO" operator-recall PDF with
 * PdfBox-Android: page-1 text-layer words, plus (for Adobe LiveCycle forms) AcroForm text-field
 * widget values, which live only in the widget appearance and never in the content stream.
 * Blocking; call on [kotlinx.coroutines.Dispatchers.IO]. Returns `null` when page 1 isn't the
 * known operator-form template, the document has no pages, or the page is rotated (the template
 * is portrait/unrotated and the widget-rect transform below assumes no rotation).
 */
object OperatorPdfFieldsReader {

    fun read(context: Context, file: File): OperatorPdfFields? {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { document ->
            if (document.numberOfPages == 0) return null
            val page = document.getPage(0)
            if (page.rotation != 0) return null

            val items = mutableListOf<PdfTextItem>()
            items += extractWords(document)
            items += extractWidgetValues(document, page)

            return OperatorPdfParser.parse(items)
        }
    }

    private fun extractWords(document: PDDocument): List<PdfTextItem> {
        val items = mutableListOf<PdfTextItem>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                var wordStart = 0
                for (i in 0..textPositions.size) {
                    val atEnd = i == textPositions.size
                    val blank = !atEnd && textPositions[i].unicode.isNullOrBlank()
                    if (atEnd || blank) {
                        if (i > wordStart) items += buildWord(textPositions.subList(wordStart, i))
                        wordStart = i + 1
                    }
                }
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = 1
        stripper.endPage = 1
        stripper.getText(document)
        return items
    }

    private fun buildWord(glyphs: List<TextPosition>): PdfTextItem {
        val sb = StringBuilder()
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (tp in glyphs) {
            sb.append(tp.unicode)
            // Some producers (e.g. Microsoft Print to PDF's embedded CID fonts) report a
            // fontSizeInPt wildly larger than the glyph's actual rendered size; heightDir
            // reflects the real device-space glyph box, so distrust fontSizeInPt when it's far
            // out of line with heightDir.
            val heightDirSize = tp.heightDir * 2f
            val size = tp.fontSizeInPt.takeIf { it > 0f && it <= heightDirSize * 2f } ?: heightDirSize
            val glyphLeft = tp.xDirAdj
            val glyphRight = tp.xDirAdj + tp.widthDirAdj
            val glyphTop = tp.yDirAdj - 0.75f * size
            val glyphBottom = tp.yDirAdj + 0.2f * size
            if (glyphLeft < left) left = glyphLeft
            if (glyphRight > right) right = glyphRight
            if (glyphTop < top) top = glyphTop
            if (glyphBottom > bottom) bottom = glyphBottom
        }
        return PdfTextItem(sb.toString(), left, top, right, bottom, isWidget = false)
    }

    private fun extractWidgetValues(document: PDDocument, page: PDPage): List<PdfTextItem> {
        val acroForm = document.documentCatalog.acroForm ?: return emptyList()
        val pageAnnots = Collections.newSetFromMap(IdentityHashMap<COSDictionary, Boolean>())
        for (annotation in page.annotations) pageAnnots += annotation.getCOSObject()
        val crop = page.cropBox

        val items = mutableListOf<PdfTextItem>()
        for (field in acroForm.fieldTree) {
            val textField = field as? PDTextField ?: continue
            val value = textField.valueAsString.replace(Regex("\\s+"), " ").trim()
            if (value.isEmpty()) continue
            for (widget in textField.widgets) {
                if (widget.getCOSObject() !in pageAnnots) continue
                val r = widget.rectangle ?: continue
                items += PdfTextItem(
                    text = value,
                    left = r.lowerLeftX - crop.lowerLeftX,
                    top = crop.upperRightY - r.upperRightY,
                    right = r.upperRightX - crop.lowerLeftX,
                    bottom = crop.upperRightY - r.lowerLeftY,
                    isWidget = true,
                )
            }
        }
        return items
    }
}
