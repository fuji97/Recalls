package it.federicorapetti.recalls.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.federicorapetti.recalls.data.RecallRepository
import it.federicorapetti.recalls.data.RecallStore
import it.federicorapetti.recalls.data.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecallListUiState(
    val groups: List<RecallDayGroup> = emptyList(),
    val filter: RegionFilter = RegionFilter.ALL,
    val query: String = "",
    val unreadOnly: Boolean = false,
    val unreadCounts: Map<RegionFilter, Int> = emptyMap(),
    val syncProgress: Float? = null,
    val lastSync: Long? = null,
    val error: String? = null
)

class RecallListViewModel(
    private val repository: RecallRepository,
    private val store: RecallStore,
    private val settings: SettingsRepository
) : ViewModel() {

    private val filter = MutableStateFlow(RegionFilter.ALL)
    private val query = MutableStateFlow("")
    private val unreadOnly = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null

    private val listQuery = combine(filter, query, unreadOnly) { f, q, u -> RecallListQuery(f, q, u) }

    val uiState: StateFlow<RecallListUiState> = combine(
        store.observeAll(),
        listQuery,
        store.observeLastSync(),
        repository.syncProgress,
        error
    ) { all, q, lastSync, progress, err ->
        val view = buildRecallListView(all, q)
        RecallListUiState(
            groups = view.groups,
            filter = q.filter,
            query = q.text,
            unreadOnly = q.unreadOnly,
            unreadCounts = view.unreadCounts,
            syncProgress = progress,
            lastSync = lastSync,
            error = err
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecallListUiState())

    init {
        viewModelScope.launch {
            val lastSync = store.observeLastSync().first()
            if (lastSync == null || System.currentTimeMillis() - lastSync > ONE_HOUR_MS) {
                refresh()
            }
        }
    }

    fun setFilter(value: RegionFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setUnreadOnly(value: Boolean) {
        unreadOnly.value = value
    }

    val notifPermissionAsked = settings.notifPermissionAsked

    fun onNotifPermissionAsked() {
        viewModelScope.launch { settings.setNotifPermissionAsked(true) }
    }

    fun consumeError() {
        error.value = null
    }

    fun markAllRead() {
        viewModelScope.launch { store.markAllRead() }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val result = repository.sync()
            error.value = if (result.errors.isNotEmpty()) {
                result.errors.keys.joinToString(", ") { it.name }
            } else {
                null
            }
        }
    }

    companion object {
        private const val ONE_HOUR_MS = 60 * 60 * 1000L
    }
}
