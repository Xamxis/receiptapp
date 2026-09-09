package com.example.receiptapp.data.repository

import android.graphics.Bitmap
import com.example.receiptapp.data.local.ReceiptDao
import com.example.receiptapp.data.model.ReceiptWithItems
import com.example.receiptapp.data.remote.AiParseResult
import com.example.receiptapp.data.remote.ReceiptAiParser
import com.example.receiptapp.scanner.ReceiptScanner
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanAndSaveResult {
    data class Success(val receiptId: Long, val confidence: Float) : ScanAndSaveResult
    data class LowConfidence(val receiptId: Long, val confidence: Float) : ScanAndSaveResult
    data class OcrFailed(val message: String) : ScanAndSaveResult
    data class AiFailed(val reason: String) : ScanAndSaveResult
}

/**
 * Orchestriert den kompletten Scan-Flow: OCR -> KI-Parsing -> Persistenz.
 * Einzige Stelle, die UI-Layer für Beleg-bezogene Daten anspricht (Repository-Pattern).
 */
@Singleton
class ReceiptRepository @Inject constructor(
    private val scanner: ReceiptScanner,
    private val aiParser: ReceiptAiParser,
    private val dao: ReceiptDao
) {
    companion object {
        /** Unterhalb dieses Werts wird der Nutzer zur manuellen Prüfung aufgefordert. */
        private const val CONFIDENCE_REVIEW_THRESHOLD = 0.6f
    }

    suspend fun scanParseAndSave(bitmap: Bitmap): ScanAndSaveResult {
        val scanResult = try {
            scanner.recognizeText(bitmap)
        } catch (e: Exception) {
            return ScanAndSaveResult.OcrFailed(e.message ?: "OCR fehlgeschlagen")
        }

        if (scanResult.rawText.isBlank()) {
            return ScanAndSaveResult.OcrFailed("Kein Text im Bild erkannt. Bitte erneut fotografieren.")
        }

        return when (val aiResult = aiParser.parse(scanResult.rawText)) {
            is AiParseResult.Failure -> ScanAndSaveResult.AiFailed(aiResult.reason)
            is AiParseResult.Success -> {
                val receiptId = dao.insertReceiptWithItems(aiResult.receipt, aiResult.items)
                if (aiResult.receipt.aiConfidence < CONFIDENCE_REVIEW_THRESHOLD) {
                    ScanAndSaveResult.LowConfidence(receiptId, aiResult.receipt.aiConfidence)
                } else {
                    ScanAndSaveResult.Success(receiptId, aiResult.receipt.aiConfidence)
                }
            }
        }
    }

    fun observeAllReceipts(): Flow<List<ReceiptWithItems>> = dao.observeAllWithItems()

    fun observeReceiptsInRange(start: LocalDate, end: LocalDate): Flow<List<ReceiptWithItems>> =
        dao.observeInRange(start.toString(), end.toString())

    fun observeReceipt(id: Long): Flow<ReceiptWithItems?> = dao.observeReceiptWithItems(id)

    suspend fun deleteReceipt(id: Long) = dao.deleteReceipt(id)
}
