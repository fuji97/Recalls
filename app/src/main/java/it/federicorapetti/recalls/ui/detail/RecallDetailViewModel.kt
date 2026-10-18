package it.federicorapetti.recalls.ui.detail

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.federicorapetti.recalls.data.RecallRepository
import it.federicorapetti.recalls.data.RecallStore
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.remote.safetygate.SgDetail
import it.federicorapetti.recalls.data.remote.salute.OperatorPdfFields
import it.federicorapetti.recalls.data.remote.salute.OperatorPdfLoader
import it.federicorapetti.recalls.data.remote.salute.OperatorPdfPart
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

sealed interface PdfPhotosState {
    data object Loading : PdfPhotosState
    data class Loaded(val photos: List<Bitmap>) : PdfPhotosState
}

class RecallDetailViewModel(
    private val id: String,
    private val store: RecallStore,
    private val repository: RecallRepository,
    private val pdfLoader: OperatorPdfLoader
) : ViewModel() {

    val item: StateFlow<RecallEntity?> = store.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _sgDetail = MutableStateFlow<SgDetailState>(SgDetailState.Loading)
    val sgDetail: StateFlow<SgDetailState> = _sgDetail

    private val _pdfPhotos = MutableStateFlow<PdfPhotosState>(PdfPhotosState.Loading)
    val pdfPhotos: StateFlow<PdfPhotosState> = _pdfPhotos

    private val _pdfFields = MutableStateFlow<OperatorPdfFields?>(null)
    val pdfFields: StateFlow<OperatorPdfFields?> = _pdfFields

    private var markedRead = false

    init {
        viewModelScope.launch {
            val entity = store.observe(id).filterNotNull().first()
            if (!markedRead) {
                markedRead = true
                store.markRead(id)
            }
            val attachmentUrl = entity.attachmentUrl
            when (entity.source) {
                RecallSource.SAFETY_GATE ->
                    loadSafetyGateDetail(entity.remoteId)
                RecallSource.IT_OPERATOR ->
                    if (attachmentUrl != null) {
                        pdfLoader.load(attachmentUrl).collect { part ->
                            when (part) {
                                is OperatorPdfPart.Photos -> _pdfPhotos.value = PdfPhotosState.Loaded(part.photos)
                                is OperatorPdfPart.Fields -> _pdfFields.value = part.fields
                            }
                        }
                    } else {
                        _pdfPhotos.value = PdfPhotosState.Loaded(emptyList())
                    }
                RecallSource.IT_MINISTRY -> Unit
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
