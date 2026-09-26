package it.federicorapetti.recalls.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.federicorapetti.recalls.data.RecallRepository
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.remote.salute.ROME
import it.federicorapetti.recalls.data.settings.SettingsRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecallDayGroup(val date: LocalDate, val items: List<RecallEntity>)

data class RecallListUiState(
    val groups: List<RecallDayGroup> = emptyList(),
    val filter: SourceFilter = SourceFilter.ALL,
    val query: String = "",
    val unreadOnly: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastSync: Long? = null,
    val error: String? = null
)

private data class FilterState(val filter: SourceFilter, val query: String, val unreadOnly: Boolean)

class RecallListViewModel(
    private val repository: RecallRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val filter = MutableStateFlow(SourceFilter.ALL)
    private val query = MutableStateFlow("")
    private val unreadOnly = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val filterState = combine(filter, query, unreadOnly) { f, q, u -> FilterState(f, q, u) }

    val uiState: StateFlow<RecallListUiState> = combine(
        repository.observeAll(),
        filterState,
        repository.observeLastSync(),
        isRefreshing,
        error
    ) { all, fs, lastSync, refreshing, err ->
        RecallListUiState(
            groups = buildGroups(all, fs),
            filter = fs.filter,
            query = fs.query,
            unreadOnly = fs.unreadOnly,
            isRefreshing = refreshing,
            lastSync = lastSync,
            error = err
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecallListUiState())

    init {
        viewModelScope.launch {
            val lastSync = repository.observeLastSync().first()
            if (lastSync == null || System.currentTimeMillis() - lastSync > ONE_HOUR_MS) {
                refresh()
            }
        }
    }

    fun setFilter(value: SourceFilter) {
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
        viewModelScope.launch { repository.markAllRead() }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            val result = repository.sync()
            error.value = if (result.errors.isNotEmpty()) {
                result.errors.keys.joinToString(", ") { it.name }
            } else {
                null
            }
            isRefreshing.value = false
        }
    }

    private fun buildGroups(all: List<RecallEntity>, fs: FilterState): List<RecallDayGroup> {
        val filtered = all.asSequence()
            .filter { matchesFilter(it, fs.filter) }
            .filter { !fs.unreadOnly || (it.isNew && !it.isRead) }
            .filter { matchesQuery(it, fs.query) }
            .toList()

        return filtered
            .groupBy { Instant.ofEpochMilli(it.publishedAt).atZone(ROME).toLocalDate() }
            .map { (date, items) -> RecallDayGroup(date, items) }
    }

    private fun matchesFilter(entity: RecallEntity, filter: SourceFilter): Boolean = when (filter) {
        SourceFilter.ALL -> true
        SourceFilter.EU -> entity.source == RecallSource.SAFETY_GATE
        SourceFilter.IT -> entity.source.isItaly
    }

    private fun matchesQuery(entity: RecallEntity, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return listOfNotNull(entity.title, entity.subtitle, entity.brand, entity.reason)
            .any { it.contains(q, ignoreCase = true) }
    }

    companion object {
        private const val ONE_HOUR_MS = 60 * 60 * 1000L
    }
}
