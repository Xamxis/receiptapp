package com.example.receiptapp.data.model

/**
 * Berechneter Gesundheits-Score für einen Zeitraum (z. B. eine Woche).
 * Wird NICHT in Room persistiert, sondern zur Laufzeit aus ReceiptItems
 * berechnet (siehe CalculateNutritionScoreUseCase) – so bleibt er immer
 * konsistent mit ggf. nachträglich korrigierten Postendaten.
 */
data class NutritionScore(
    val score: Int, // 0..100
    val freshSharePercent: Int,
    val processedSharePercent: Int,
    val fastFoodSharePercent: Int,
    val tips: List<HealthTip>
)

data class HealthTip(
    val title: String,
    val detail: String,
    val relatedItemNames: List<String> = emptyList()
)
