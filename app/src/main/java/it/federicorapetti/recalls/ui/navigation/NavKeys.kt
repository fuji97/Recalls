package it.federicorapetti.recalls.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object RecallsListKey : NavKey

@Serializable
data class RecallDetailKey(val id: String) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object ScanKey : NavKey
