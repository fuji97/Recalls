package it.federicorapetti.recalls.ui.navigation

import it.federicorapetti.recalls.ui.list.SourceFilter

sealed interface LaunchRequest {
    data class Detail(val id: String) : LaunchRequest
    data class Filter(val filter: SourceFilter) : LaunchRequest
}
