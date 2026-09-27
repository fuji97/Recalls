package it.federicorapetti.recalls.data.remote.salute

/** A word (or a whole form-field value) on page 1, in top-down PDF points. */
data class PdfTextItem(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val isWidget: Boolean,
)

/** Values read from a Ministry "RICHIAMO" operator form; null = field absent/blank. */
data class OperatorPdfFields(
    val productName: String?, // Denominazione di vendita
    val brand: String?, // Marchio del prodotto
    val operator: String?, // Nome o ragione sociale dell'OSA …
    val lot: String?, // Lotto di produzione
    val plantMark: String?, // Marchio di identificazione dello stabilimento/del produttore
    val producer: String?, // Nome del produttore
    val plantSite: String?, // Sede dello stabilimento
    val expiry: String?, // Data di scadenza o termine minimo di conservazione
    val quantity: String?, // Descrizione peso/volume unità di vendita
    val reasonDetail: String?, // Motivo del richiamo / della revoca
    val warnings: String?, // Avvertenze
)

/**
 * Parses the values of a Ministero della Salute "RICHIAMO" operator-recall PDF template out of
 * its page-1 text/widget items. Returns `null` when the page doesn't look like that template
 * (fewer than 10 of the known labels are present), so scans, image-only prints and free-form
 * letters yield no data.
 */
object OperatorPdfParser {

    /** Declaration order is also the per-line matching order. */
    private enum class LabelKind(val pattern: Regex, val isField: Boolean) {
        EXPIRY(Regex("""dat[ae] di scadenza o termine minimo di conservazione\s*:?"""), true),
        DATE(Regex("""(?<![\w'])data\s*:"""), true),
        BRAND(Regex("""marchio del prodotto\s*:?"""), true),
        PRODUCT(Regex("""denominazione di vendita\s*:?"""), true),
        OPERATOR(Regex("""nome o ragione sociale(?:\s+dell'osa)?\s*:?"""), true),
        LOT(Regex("""lott[oi] di produzione\s*:?"""), true),
        PLANT_MARK(Regex("""marchio di identificazione dello stabilimento(?:\s*/\s*del produttore)?\s*:?"""), true),
        PRODUCER(Regex("""nome del produttore\s*:?"""), true),
        PLANT_SITE(Regex("""sede dello stabilimento\s*:?"""), true),
        QUANTITY(Regex("""descrizione peso\s*/\s*volume(?:\s+unit[aà])?(?:\s+di vendita)?\s*:?"""), true),
        REASON(Regex("""motivo del(?:la)? (?:richiamo|revoca)\s*:?"""), true),
        WARNINGS(Regex("""avvertenze\s*:"""), true),
        IMAGE(Regex("""(?:inserire\s+)?immagine\s+(?:uno|due|\d+)\s*:?"""), false),
    }

    private val OSA_CONTINUATION = Regex(
        """^(?:dell'osa(?=\s|$)\s*)?(?:a nome del quale(?=\s|$)\s*(?:il(?=\s|$)\s*)?)?""" +
            """(?:prodotto\s+[eè](?=\s|$)\s*)?(?:commercializzato\s*:?)?"""
    )

    /** A [PdfTextItem] plus a stable identity index, so sets can track "already consumed" items. */
    private class Word(val item: PdfTextItem, val index: Int) {
        val left get() = item.left
        val right get() = item.right
        val top get() = item.top
        val bottom get() = item.bottom
        val text get() = item.text
        val isWidget get() = item.isWidget
        val centerY get() = (item.top + item.bottom) / 2f
        val height get() = item.bottom - item.top
    }

    private class Label(val kind: LabelKind, val left: Float, val top: Float, val height: Float)

    private class Row(val top: Float, val height: Float, val labels: List<Label>)

    private class LineInfo(val words: List<Word>, val normText: String, val spans: List<IntRange>)

    fun parse(items: List<PdfTextItem>): OperatorPdfFields? {
        val words = items.mapIndexed { index, item -> Word(item, index) }
        val nonWidgetWords = words.filter { !it.isWidget }

        val lineInfos = groupIntoLines(nonWidgetWords, 0.3f).map(::buildLineInfo)

        val labelItemIndices = mutableSetOf<Int>()
        val foundFieldKinds = mutableSetOf<LabelKind>()
        val labels = mutableListOf<Label>()

        for (info in lineInfos) {
            val claimed = BooleanArray(info.normText.length)
            for (kind in LabelKind.entries) {
                for (match in kind.pattern.findAll(info.normText)) {
                    if (kind.isField && kind in foundFieldKinds) break
                    val start = match.range.first
                    val end = match.range.last + 1
                    if ((start until end).any { claimed[it] }) continue
                    val before = info.normText.substring(0, start)
                    val endsWithColon = end > 0 && info.normText[end - 1] == ':'
                    if (!(before.isBlank() || endsWithColon)) continue

                    for (i in start until end) claimed[i] = true
                    val matchedWords = info.words.indices
                        .filter { idx -> info.spans[idx].first < end && info.spans[idx].last + 1 > start }
                        .map { idx -> info.words[idx] }
                    for (w in matchedWords) labelItemIndices += w.index
                    val labelLeft = matchedWords.minOf { it.left }
                    val labelTop = matchedWords.minOf { it.top }
                    val labelBottom = matchedWords.maxOf { it.bottom }
                    labels += Label(kind, labelLeft, labelTop, labelBottom - labelTop)
                    if (kind.isField) foundFieldKinds += kind
                }
            }
        }

        if (foundFieldKinds.size < 10) return null

        val rows = buildRows(labels)
        fun rowFor(y: Float): Row? = rows.lastOrNull { it.top - it.height <= y }

        // Remaining OSA label lines: continuation lines of the split "Nome o ragione sociale …" label.
        val operatorLabel = labels.firstOrNull { it.kind == LabelKind.OPERATOR }
        if (operatorLabel != null) {
            val operatorRow = rows.firstOrNull { row -> row.labels.any { it === operatorLabel } }
            if (operatorRow != null) {
                for (info in lineInfos) {
                    val first = info.words.firstOrNull() ?: continue
                    if (first.index in labelItemIndices) continue
                    if (kotlin.math.abs(first.left - operatorLabel.left) > 4f) continue
                    if (rowFor(first.centerY) !== operatorRow) continue
                    val match = OSA_CONTINUATION.find(info.normText) ?: continue
                    if (match.value.isBlank()) continue
                    val matchEnd = match.range.last + 1
                    for (idx in info.words.indices) {
                        if (info.spans[idx].first < matchEnd) labelItemIndices += info.words[idx].index
                    }
                }
            }
        }

        // Assign every remaining (non-label) item, including widgets, to the field that owns its row.
        val fieldItems = mutableMapOf<LabelKind, MutableList<Word>>()
        for (word in words) {
            if (word.index in labelItemIndices) continue
            val row = rowFor(word.centerY) ?: continue
            val owner = row.labels.lastOrNull { it.left <= word.left + 2f } ?: row.labels.first()
            if (owner.kind == LabelKind.DATE || owner.kind == LabelKind.IMAGE) continue
            fieldItems.getOrPut(owner.kind) { mutableListOf() } += word
        }

        fun assemble(kind: LabelKind): String? = assembleField(fieldItems[kind].orEmpty())

        return OperatorPdfFields(
            productName = assemble(LabelKind.PRODUCT),
            brand = assemble(LabelKind.BRAND),
            operator = assemble(LabelKind.OPERATOR),
            lot = assemble(LabelKind.LOT),
            plantMark = assemble(LabelKind.PLANT_MARK),
            producer = assemble(LabelKind.PRODUCER),
            plantSite = assemble(LabelKind.PLANT_SITE),
            expiry = assemble(LabelKind.EXPIRY),
            quantity = assemble(LabelKind.QUANTITY),
            reasonDetail = assemble(LabelKind.REASON),
            warnings = assemble(LabelKind.WARNINGS),
        )
    }

    private fun buildRows(labels: List<Label>): List<Row> {
        val sorted = labels.sortedBy { it.top }
        val rowLabels = mutableListOf<MutableList<Label>>()
        for (label in sorted) {
            val current = rowLabels.lastOrNull()
            if (current != null && label.top - current.first().top < 0.5f * label.height) {
                current += label
            } else {
                rowLabels += mutableListOf(label)
            }
        }
        return rowLabels.map { group ->
            Row(
                top = group.first().top,
                height = group.maxOf { it.height },
                labels = group.sortedBy { it.left },
            )
        }
    }

    private fun buildLineInfo(lineWords: List<Word>): LineInfo {
        val sb = StringBuilder()
        val spans = mutableListOf<IntRange>()
        for ((i, w) in lineWords.withIndex()) {
            if (i > 0) sb.append(' ')
            val start = sb.length
            sb.append(w.text)
            spans += start until sb.length
        }
        return LineInfo(lineWords, normalize(sb.toString()), spans)
    }

    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    '\u2019', '\u2018' -> '\''
                    '\u00A0' -> ' '
                    else -> c.lowercaseChar()
                }
            )
        }
        return sb.toString()
    }

    /** Groups words into visual lines: same line when centers are within [coefficient] × height. */
    private fun groupIntoLines(words: List<Word>, coefficient: Float): List<List<Word>> {
        val sorted = words.sortedWith(compareBy({ it.centerY }, { it.left }))
        val lines = mutableListOf<MutableList<Word>>()
        for (w in sorted) {
            val current = lines.lastOrNull()
            val first = current?.firstOrNull()
            if (current != null && first != null &&
                kotlin.math.abs(w.centerY - first.centerY) <= coefficient * minOf(w.height, first.height)
            ) {
                current += w
            } else {
                lines += mutableListOf(w)
            }
        }
        return lines.map { line -> line.sortedBy { it.left } }
    }

    private fun assembleField(fieldWords: List<Word>): String? {
        if (fieldWords.isEmpty()) return null
        val lines = groupIntoLines(fieldWords, 0.5f).sortedBy { line -> line.minOf { it.centerY } }
        val rendered = lines.joinToString(" ") { line ->
            val sb = StringBuilder()
            for ((i, w) in line.withIndex()) {
                if (i > 0) {
                    val prev = line[i - 1]
                    if (w.left - prev.right > 0.1f * prev.height) sb.append(' ')
                }
                sb.append(w.text)
            }
            sb.toString()
        }
        val collapsed = rendered.replace(Regex("\\s+"), " ").trim()
        return collapsed.takeIf { value -> value.any { it.isLetterOrDigit() } }
    }
}
