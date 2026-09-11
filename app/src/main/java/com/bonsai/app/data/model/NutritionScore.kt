package com.bonsai.app.data.model

/**
 * Ergebnis des Beraters für einen Zeitraum.
 * Wird zur Laufzeit aus den ReceiptItems berechnet (nicht persistiert), damit er
 * immer zu ggf. nachträglich korrigierten Posten passt.
 */
data class NutritionScore(
    val score: Int,               // 0..100
    val freshSharePercent: Int,
    val processedSharePercent: Int,
    val fastFoodSharePercent: Int,
    val tips: List<HealthTip>,
    val hasData: Boolean = true
)

/** Art des Hinweises – steuert Farbe und Icon der Tipp-Karte im UI. */
enum class TipKind { ERNAEHRUNG, AUSGABEN, INFO }

data class HealthTip(
    val kind: TipKind,
    val title: String,
    val detail: String,
    val relatedItemNames: List<String> = emptyList()
)
