package it.federicorapetti.recalls.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.federicorapetti.recalls.data.RecallRepository
import it.federicorapetti.recalls.data.RecallStore
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.remote.safetygate.SgDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SgDetailState {
    data object Loading : SgDetailState
    data class Loaded(val detail: SgDetail) : SgDetailState
    data class Failed(val error: Throwable) : SgDetailState
}

class RecallDetailViewModel(
    private val id: String,
    private val store: RecallStore,
    private val repository: RecallRepository
) : ViewModel() {

    val item: StateFlow<RecallEntity?> = store.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _sgDetail = MutableStateFlow<SgDetailState>(SgDetailState.Loading)
    val sgDetail: StateFlow<SgDetailState> = _sgDetail

    private var markedRead = false

    init {
        viewModelScope.launch {
            val entity = store.observe(id).filterNotNull().first()
            if (!markedRead) {
                markedRead = true
                store.markRead(id)
            }
            when (entity.source) {
                RecallSource.SAFETY_GATE -> loadSafetyGateDetail(entity.remoteId)
                // Operator photos and fields are stored at sync (OperatorPdfArchive); nothing to load.
                RecallSource.IT_OPERATOR, RecallSource.IT_MINISTRY -> Unit
            }
        }
    }

    fun retry() {
        val entity = item.value ?: return
        if (entity.source == RecallSource.SAFETY_GATE) {
            viewModelScope.launch { loadSafetyGateDetail(entity.remoteId) }
        }
    }

    private suspend fun loadSafetyGateDetail(remoteId: String) {
        _sgDetail.value = SgDetailState.Loading
        _sgDetail.value = try {
            SgDetailState.Loaded(repository.safetyGateDetail(remoteId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SgDetailState.Failed(e)
        }
    }
}
