package it.federicorapetti.recalls.ui.navigation

import android.content.Intent
import it.federicorapetti.recalls.ui.list.RegionFilter

private const val EXTRA_RECALL_ID = "recall_id"
private const val EXTRA_SOURCE_FILTER = "source_filter"
private const val FILTER_EU = "EU"
private const val FILTER_IT = "IT"

sealed interface LaunchRequest {
    data class Detail(val id: String) : LaunchRequest
    data class Filter(val filter: RegionFilter) : LaunchRequest

    /** Encodes this request into a notification tap intent. Filter(ALL) writes nothing. */
    fun writeTo(intent: Intent) {
        when (this) {
            is Detail -> intent.putExtra(EXTRA_RECALL_ID, id)
            is Filter -> when (filter) {
                RegionFilter.EU -> intent.putExtra(EXTRA_SOURCE_FILTER, FILTER_EU)
                RegionFilter.ITALY -> intent.putExtra(EXTRA_SOURCE_FILTER, FILTER_IT)
                RegionFilter.ALL -> Unit
            }
        }
    }

    companion object {
        /** Decodes a tap intent; Detail wins when both extras exist; unknown or missing -> null. */
        fun from(intent: Intent): LaunchRequest? {
            intent.getStringExtra(EXTRA_RECALL_ID)?.let { return Detail(it) }
            return when (intent.getStringExtra(EXTRA_SOURCE_FILTER)) {
                FILTER_EU -> Filter(RegionFilter.EU)
                FILTER_IT -> Filter(RegionFilter.ITALY)
                else -> null
            }
        }
    }
}
