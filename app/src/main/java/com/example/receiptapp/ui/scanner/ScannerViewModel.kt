package com.example.receiptapp.ui.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.receiptapp.data.repository.ReceiptRepository
import com.example.receiptapp.data.repository.ScanAndSaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScannerUiState {
    data object Idle : ScannerUiState
    data object Processing : ScannerUiState
    data class Success(val receiptId: Long) : ScannerUiState
    data class NeedsReview(val receiptId: Long, val confidence: Float) : ScannerUiState
    data class Error(val message: String) : ScannerUiState
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val repository: ReceiptRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val uiState: StateFlow<ScannerUiState> = _uiState

    fun onPhotoCaptured(bitmap: Bitmap) {
        _uiState.value = ScannerUiState.Processing
        viewModelScope.launch {
            when (val result = repository.scanParseAndSave(bitmap)) {
                is ScanAndSaveResult.Success -> _uiState.value = ScannerUiState.Success(result.receiptId)
                is ScanAndSaveResult.LowConfidence -> _uiState.value =
                    ScannerUiState.NeedsReview(result.receiptId, result.confidence)
                is ScanAndSaveResult.OcrFailed -> _uiState.value = ScannerUiState.Error(result.message)
                is ScanAndSaveResult.AiFailed -> _uiState.value =
                    ScannerUiState.Error("KI-Analyse fehlgeschlagen: ${result.reason}")
            }
        }
    }

    fun reset() {
        _uiState.value = ScannerUiState.Idle
    }
}
