package com.bonsai.app.ui.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bonsai.app.billing.FreeTier
import com.bonsai.app.billing.ProStatusStore
import com.bonsai.app.data.repository.ReceiptRepository
import com.bonsai.app.data.repository.ScanAndSaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScannerUiState {
    data object Idle : ScannerUiState
    data object Processing : ScannerUiState
    data class Success(val receiptId: Long, val itemCount: Int) : ScannerUiState
    data class NeedsReview(val receiptId: Long, val confidence: Float, val itemCount: Int) : ScannerUiState
    data class Error(val message: String) : ScannerUiState
    /** Gratis-Kontingent für diesen Monat aufgebraucht – Paywall anzeigen. */
    data object LimitReached : ScannerUiState
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val repository: ReceiptRepository,
    private val proStatusStore: ProStatusStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val uiState: StateFlow<ScannerUiState> = _uiState

    fun onPhotoCaptured(bitmap: Bitmap) {
        _uiState.value = ScannerUiState.Processing
        viewModelScope.launch {
            // Gratis-Version: monatliches Scan-Kontingent prüfen
            val isPro = proStatusStore.isPro.first()
            if (!isPro && proStatusStore.currentScanCount() >= FreeTier.MONTHLY_SCAN_LIMIT) {
                _uiState.value = ScannerUiState.LimitReached
                return@launch
            }

            when (val result = repository.scanParseAndSave(bitmap)) {
                is ScanAndSaveResult.Success -> {
                    proStatusStore.recordScan()
                    _uiState.value = ScannerUiState.Success(result.receiptId, result.itemCount)
                }
                is ScanAndSaveResult.LowConfidence -> {
                    proStatusStore.recordScan()
                    _uiState.value = ScannerUiState.NeedsReview(result.receiptId, result.confidence, result.itemCount)
                }
                is ScanAndSaveResult.OcrFailed ->
                    _uiState.value = ScannerUiState.Error(result.message)
                is ScanAndSaveResult.AiFailed ->
                    _uiState.value = ScannerUiState.Error(
                        "Die Belegdaten konnten nicht ausgewertet werden. ${result.reason}"
                    )
            }
        }
    }

    fun onCaptureError(message: String) {
        _uiState.value = ScannerUiState.Error(message)
    }

    fun reset() {
        _uiState.value = ScannerUiState.Idle
    }
}
