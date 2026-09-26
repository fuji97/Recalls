package it.federicorapetti.recalls.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.federicorapetti.recalls.data.RecallRepository
import it.federicorapetti.recalls.data.settings.SettingsRepository
import it.federicorapetti.recalls.sync.SyncScheduler
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val scheduler: SyncScheduler,
    repository: RecallRepository
) : ViewModel() {

    val intervalHours = settings.intervalHours
    val notifyEu = settings.notifyEu
    val notifyIt = settings.notifyIt
    val lastSync = repository.observeLastSync()

    fun setIntervalHours(hours: Int) {
        viewModelScope.launch {
            settings.setIntervalHours(hours)
            scheduler.schedulePeriodic(hours)
        }
    }

    fun setNotifyEu(enabled: Boolean) {
        viewModelScope.launch { settings.setNotifyEu(enabled) }
    }

    fun setNotifyIt(enabled: Boolean) {
        viewModelScope.launch { settings.setNotifyIt(enabled) }
    }

    fun checkNow() {
        scheduler.syncNow()
    }
}
