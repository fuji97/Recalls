package it.federicorapetti.recalls.ui.detail

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.federicorapetti.recalls.data.RecallRepository
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.remote.downloadTo
import it.federicorapetti.recalls.data.remote.safetygate.SgDetail
import it.federicorapetti.recalls.data.remote.salute.OperatorPdfFields
import it.federicorapetti.recalls.data.remote.salute.OperatorPdfFieldsReader
import it.federicorapetti.recalls.image.PdfPhotoExtractor
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

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
    private val repository: RecallRepository,
    private val context: Context,
    private val httpClient: OkHttpClient,
) : ViewModel() {

    val item: StateFlow<RecallEntity?> = repository.observe(id)
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
            val entity = repository.observe(id).filterNotNull().first()
            if (!markedRead) {
                markedRead = true
                repository.markRead(id)
            }
            val attachmentUrl = entity.attachmentUrl
            when (entity.source) {
                RecallSource.SAFETY_GATE ->
                    loadSafetyGateDetail(entity.remoteId)
                RecallSource.IT_OPERATOR ->
                    if (attachmentUrl != null) {
                        loadPdf(attachmentUrl)
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

    /**
     * Downloads the recall PDF once and feeds both the photo carousel and the form-field rows
     * from that single copy. Photos publish first so the carousel never waits on the PDFBox
     * parse. Every failure degrades gracefully: a download failure or corrupt PDF yields no
     * photos and no fields; a PDF that isn't the known operator-form template yields no fields
     * (and possibly still photos, since photo boxes are template-independent).
     */
    private suspend fun loadPdf(attachmentUrl: String) = withContext(Dispatchers.IO) {
        _pdfPhotos.value = PdfPhotosState.Loading
        val file = File.createTempFile("recall", ".pdf", context.cacheDir)
        try {
            try {
                httpClient.downloadTo(attachmentUrl, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pdfPhotos.value = PdfPhotosState.Loaded(emptyList())
                return@withContext
            }
            _pdfPhotos.value = PdfPhotosState.Loaded(
                try {
                    PdfPhotoExtractor.extract(file)
                } catch (e: Exception) {
                    emptyList()
                }
            )
            _pdfFields.value = try {
                OperatorPdfFieldsReader.read(context, file)
            } catch (e: Exception) {
                null
            } catch (e: LinkageError) {
                null // BouncyCastle is excluded; a certificate-encrypted PDF would hit a missing class.
            }
        } finally {
            file.delete()
        }
    }
}
