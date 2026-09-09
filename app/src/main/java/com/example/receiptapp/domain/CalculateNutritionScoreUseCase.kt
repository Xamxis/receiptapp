package com.example.receiptapp.domain

import com.example.receiptapp.data.model.HealthTip
import com.example.receiptapp.data.model.NutritionScore
import com.example.receiptapp.data.model.NutritionTag
import com.example.receiptapp.data.model.ReceiptWithItems
import javax.inject.Inject

/**
 * Berechnet den Gesundheits-Score (0-100) aus allen ReceiptItems eines Zeitraums.
 * Bewusst als reine Funktion ohne Seiteneffekte -> einfach testbar.
 */
class CalculateNutritionScoreUseCase @Inject constructor() {

    operator fun invoke(receipts: List<ReceiptWithItems>): NutritionScore {
        val foodItems = receipts.flatMap { it.items }.filter {
            it.nutritionTag != null && it.nutritionTag != NutritionTag.NICHT_LEBENSMITTEL
        }

        if (foodItems.isEmpty()) {
            return NutritionScore(score = 100, freshSharePercent = 0, processedSharePercent = 0, fastFoodSharePercent = 0, tips = emptyList())
        }

        val totalWeight = foodItems.sumOf { it.nutritionTag!!.scoreWeight }
        val maxPossible = foodItems.size * 10 // FRISCH_UNVERARBEITET = höchster Weight
        val normalizedScore = (((totalWeight.toDouble() / maxPossible) + 1) / 2 * 100)
            .coerceIn(0.0, 100.0)
            .toInt()

        val freshCount = foodItems.count { it.nutritionTag == NutritionTag.FRISCH_UNVERARBEITET || it.nutritionTag == NutritionTag.VOLLWERTIG }
        val processedCount = foodItems.count { it.nutritionTag == NutritionTag.VERARBEITET }
        val fastFoodCount = foodItems.count { it.nutritionTag == NutritionTag.STARK_VERARBEITET }

        fun percent(count: Int) = (count * 100.0 / foodItems.size).toInt()

        val tips = buildTips(foodItems.map { it.name to it.nutritionTag!! })

        return NutritionScore(
            score = normalizedScore,
            freshSharePercent = percent(freshCount),
            processedSharePercent = percent(processedCount),
            fastFoodSharePercent = percent(fastFoodCount),
            tips = tips
        )
    }

    private fun buildTips(items: List<Pair<String, NutritionTag>>): List<HealthTip> {
        val tips = mutableListOf<HealthTip>()

        val repeatedSnacks = items
            .filter { it.second == NutritionTag.STARK_VERARBEITET }
            .groupBy { it.first }
            .filter { it.value.size >= 3 }

        repeatedSnacks.forEach { (name, occurrences) ->
            tips += HealthTip(
                title = "Häufiger Kauf: $name",
                detail = "Du hast \"$name\" ${occurrences.size}x gekauft. Eine frischere Alternative " +
                    "könnte langfristig Energie-Level und Budget verbessern.",
                relatedItemNames = listOf(name)
            )
        }

        val sugarCount = items.count { it.second == NutritionTag.ZUCKERHALTIG }
        if (sugarCount >= 3) {
            tips += HealthTip(
                title = "Viele zuckerhaltige Käufe",
                detail = "$sugarCount zuckerhaltige Produkte in diesem Zeitraum. Wasser, ungesüßter Tee " +
                    "oder Infused Water sind kalorienfreie Alternativen."
            )
        }

        if (tips.isEmpty()) {
            tips += HealthTip(
                title = "Guter Kurs!",
                detail = "Deine Einkäufe in diesem Zeitraum sind überwiegend ausgewogen. Weiter so."
            )
        }

        return tips
    }
}
