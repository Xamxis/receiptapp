package com.example.receiptapp.scanner

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Kapselt ML Kit Text Recognition (on-device, offline, kostenlos).
 * Nimmt ein Bitmap (aus CameraX-Aufnahme) und liefert den erkannten Rohtext.
 */
@Singleton
class ReceiptScanner @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap): ScanResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks.flatMap { block -> block.lines.map { it.text } }
                cont.resume(
                    ScanResult(
                        rawText = visionText.text,
                        lineByLine = lines,
                        averageConfidenceHint = estimateConfidence(visionText.textBlocks.size, lines.size)
                    )
                )
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    /**
     * ML Kit liefert keine direkte Gesamt-Confidence; wir nähern sie grob über
     * Textmenge/Blockanzahl an, nur als Hinweis fürs UI ("Beleg schlecht lesbar?").
     */
    private fun estimateConfidence(blockCount: Int, lineCount: Int): Float =
        when {
            lineCount == 0 -> 0f
            lineCount < 3 -> 0.4f
            blockCount == 0 -> 0.6f
            else -> 0.85f
        }
}

data class ScanResult(
    val rawText: String,
    val lineByLine: List<String>,
    val averageConfidenceHint: Float
)
