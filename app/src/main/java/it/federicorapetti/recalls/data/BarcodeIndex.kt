package it.federicorapetti.recalls.data

import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.local.SgBarcodeEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.remote.safetygate.SG_MAX_PARALLEL_REQUESTS
import it.federicorapetti.recalls.data.remote.safetygate.SafetyGateApi
import it.federicorapetti.recalls.data.remote.salute.parseItalianDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val SG_WEEKLY_REPORT_THRESHOLD = 40
private const val SG_DETAIL_INDEX_LIMIT = 150
private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
private const val MIN_STORED_BARCODE_DIGITS = 8
private const val MIN_BARCODE_KEY_DIGITS = 7

/** Each raw entry reduced to its digits; entries shorter than 8 digits dropped; joined with a space. "" when none. */
private fun normalizeBarcodes(raw: List<String>): String =
    raw.map { entry -> entry.filter(Char::isDigit) }
        .filter { it.length >= MIN_STORED_BARCODE_DIGITS }
        .joinToString(" ")

/** Lookup key for a scanned code: digits only, leading zeros removed; null when fewer than 7 digits remain. */
private fun barcodeSearchKey(scanned: String): String? =
    scanned.filter(Char::isDigit).trimStart('0').takeIf { it.length >= MIN_BARCODE_KEY_DIGITS }

/**
 * Local index of the barcodes of Safety Gate alerts, so a scanned code can be matched to an alert
 * offline. Safety Gate has no barcode search, so the index is built from the alerts' details and
 * the official weekly reports.
 */
class BarcodeIndex(db: RecallDatabase, private val sgApi: SafetyGateApi) {
    private val dao = db.recallDao()

    /**
     * Indexes the barcodes of Safety Gate alerts stored within [historyStart] that are not indexed yet
     * (see [SgBarcodeEntity]). Above [SG_WEEKLY_REPORT_THRESHOLD] pending alerts, the official weekly XML
     * reports are used first (cheap, but missing the newest ~2 days and alphanumeric codes); the
     * remainder falls back to per-alert detail requests, capped at [SG_DETAIL_INDEX_LIMIT] per call.
     * Failures are isolated per report/detail fetch so a partial index is still saved. Never throws
     * except for cancellation: an indexing failure is logged and left for the next call.
     */
    suspend fun catchUp(historyStart: Long, lang: String) {
        try {
            index(historyStart, lang)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("BarcodeIndex", "Safety Gate barcode indexing failed", e)
        }
    }

    /** Alerts whose indexed barcodes contain [scanned] (leading zeros ignored); empty for too-short codes. */
    fun observeMatches(scanned: String): Flow<List<RecallEntity>> =
        barcodeSearchKey(scanned)?.let(dao::observeByBarcode) ?: flowOf(emptyList())

    /** Number of alerts within the History window that are not indexed yet. */
    fun observeUnindexedCount(): Flow<Int> =
        dao.observeUnindexedCount(RecallSource.SAFETY_GATE, System.currentTimeMillis() - HISTORY_WINDOW_MS)

    private suspend fun index(historyStart: Long, lang: String) {
        var pending = dao.unindexed(RecallSource.SAFETY_GATE, historyStart)
        if (pending.isEmpty()) return

        var weeklyCount = 0
        if (pending.size > SG_WEEKLY_REPORT_THRESHOLD) {
            val cutoff = pending.minOf { it.publishedAt } - ONE_DAY_MS
            val reports = sgApi.weeklyReportList().filter { ref ->
                val date = runCatching { parseItalianDate(ref.publicationDate) }.getOrNull()
                date != null && date >= cutoff
            }
            val reportPermits = Semaphore(SG_MAX_PARALLEL_REQUESTS)
            val maps = coroutineScope {
                reports.map { ref ->
                    async {
                        try {
                            reportPermits.withPermit { sgApi.weeklyReportBarcodes(ref.url) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("BarcodeIndex", "Weekly report failed: ${ref.url}", e)
                            emptyMap()
                        }
                    }
                }.awaitAll()
            }
            val byRemoteId = mutableMapOf<String, String>()
            maps.forEach { byRemoteId.putAll(it) }
            val rows = pending.mapNotNull { candidate ->
                byRemoteId[candidate.remoteId]?.let { text ->
                    SgBarcodeEntity(candidate.id, normalizeBarcodes(listOf(text)))
                }
            }
            if (rows.isNotEmpty()) dao.upsertBarcodes(rows)
            weeklyCount = rows.size
            pending = pending.filterNot { it.remoteId in byRemoteId }
        }

        val toFetch = pending.take(SG_DETAIL_INDEX_LIMIT)
        val detailPermits = Semaphore(SG_MAX_PARALLEL_REQUESTS)
        val detailRows = coroutineScope {
            toFetch.map { candidate ->
                async {
                    try {
                        val detail = detailPermits.withPermit { sgApi.detail(candidate.remoteId.toLong(), lang) }
                        SgBarcodeEntity(candidate.id, normalizeBarcodes(detail.product.barcodes.mapNotNull { it.barcode }))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll()
        }.filterNotNull()
        if (detailRows.isNotEmpty()) dao.upsertBarcodes(detailRows)

        val stillPending = pending.size - toFetch.size
        android.util.Log.i(
            "BarcodeIndex",
            "Barcode index: weekly=$weeklyCount detail=${detailRows.size} stillPending=$stillPending"
        )
    }
}
