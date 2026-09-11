package com.bonsai.app.data.repository

import android.graphics.Bitmap
import com.bonsai.app.data.local.ReceiptDao
import com.bonsai.app.data.model.ReceiptWithItems
import com.bonsai.app.data.model.TimeRange
import com.bonsai.app.data.remote.AiParseResult
import com.bonsai.app.data.remote.ReceiptAiParser
import com.bonsai.app.scanner.ReceiptScanner
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanAndSaveResult {
    data class Success(val receiptId: Long, val confidence: Float, val itemCount: Int) : ScanAndSaveResult
    data class LowConfidence(val receiptId: Long, val confidence: Float, val itemCount: Int) : ScanAndSaveResult
    data class OcrFailed(val message: String) : ScanAndSaveResult
    data class AiFailed(val reason: String) : ScanAndSaveResult
}

/**
 * Orchestriert den Scan-Flow (OCR -> KI-Parsing -> Persistenz) und liefert
 * die gespeicherten Belege gefiltert nach Zeitraum an die ViewModels.
 */
@Singleton
class ReceiptRepository @Inject constructor(
    private val scanner: ReceiptScanner,
    private val aiParser: ReceiptAiParser,
    private val dao: ReceiptDao
) {
    companion object {
        private const val CONFIDENCE_REVIEW_THRESHOLD = 0.6f
    }

    suspend fun scanParseAndSave(bitmap: Bitmap): ScanAndSaveResult {
        val scanResult = try {
            scanner.recognizeText(bitmap)
        } catch (e: Exception) {
            return ScanAndSaveResult.OcrFailed(e.message ?: "Texterkennung fehlgeschlagen")
        }

        if (scanResult.rawText.isBlank()) {
            return ScanAndSaveResult.OcrFailed(
                "Auf dem Bild war kein Text zu erkennen. Achte auf gute Beleuchtung und halte den Beleg flach."
            )
        }

        return when (val aiResult = aiParser.parse(scanResult.rawText)) {
            is AiParseResult.Failure -> ScanAndSaveResult.AiFailed(aiResult.reason)
            is AiParseResult.Success -> {
                val id = dao.insertReceiptWithItems(aiResult.receipt, aiResult.items)
                val count = aiResult.items.size
                if (aiResult.receipt.aiConfidence < CONFIDENCE_REVIEW_THRESHOLD) {
                    ScanAndSaveResult.LowConfidence(id, aiResult.receipt.aiConfidence, count)
                } else {
                    ScanAndSaveResult.Success(id, aiResult.receipt.aiConfidence, count)
                }
            }
        }
    }

    fun observeAll(): Flow<List<ReceiptWithItems>> = dao.observeAllWithItems()

    fun observeRange(range: TimeRange): Flow<List<ReceiptWithItems>> {
        val today = LocalDate.now()
        val (start, end) = when (range) {
            TimeRange.DIESE_WOCHE -> {
                val monday = today.with(WeekFields.of(Locale.GERMANY).dayOfWeek(), 1)
                monday to monday.plusDays(6)
            }
            TimeRange.DIESER_MONAT ->
                today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
            TimeRange.LETZTE_3_MONATE ->
                today.minusMonths(3) to today
            TimeRange.ALLES ->
                LocalDate.of(2000, 1, 1) to today.plusYears(1)
        }
        return dao.observeInRange(start.toString(), end.toString())
    }

    fun observeReceipt(id: Long): Flow<ReceiptWithItems?> = dao.observeReceiptWithItems(id)

    suspend fun deleteReceipt(id: Long) = dao.deleteReceipt(id)

    suspend fun deleteAll() = dao.deleteAllReceipts()
}
