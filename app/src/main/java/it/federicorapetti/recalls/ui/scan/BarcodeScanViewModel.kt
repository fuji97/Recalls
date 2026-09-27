package it.federicorapetti.recalls.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.federicorapetti.recalls.data.RecallRepository
import it.federicorapetti.recalls.data.local.RecallEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class BarcodeScanUiState(
    val scannedCode: String? = null,
    /** null while the lookup for [scannedCode] is loading. */
    val matches: List<RecallEntity>? = null,
    val unindexedCount: Int = 0
)

class BarcodeScanViewModel(private val repository: RecallRepository) : ViewModel() {

    private val scannedCode = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BarcodeScanUiState> = combine(
        scannedCode,
        scannedCode.flatMapLatest { code ->
            if (code == null) {
                flowOf(null)
            } else {
                repository.observeByBarcode(code)
                    .map<List<RecallEntity>, List<RecallEntity>?> { it }
                    .onStart { emit(null) }
            }
        },
        repository.observeUnindexedBarcodeCount()
    ) { code, matches, unindexed -> BarcodeScanUiState(code, matches, unindexed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BarcodeScanUiState())

    fun onBarcodeDetected(rawValue: String) {
        if (scannedCode.value == null) scannedCode.value = rawValue
    }

    fun scanAgain() {
        scannedCode.value = null
    }
}
