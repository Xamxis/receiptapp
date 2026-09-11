package com.bonsai.app.domain

import com.bonsai.app.data.model.Category
import com.bonsai.app.data.model.HealthTip
import com.bonsai.app.data.model.NutritionGoal
import com.bonsai.app.data.model.NutritionScore
import com.bonsai.app.data.model.NutritionTag
import com.bonsai.app.data.model.ReceiptWithItems
import com.bonsai.app.data.model.TipKind
import java.util.Locale
import javax.inject.Inject

/**
 * Der "Berater": erzeugt aus den gescannten Belegen konkrete, umsetzbare Hinweise –
 * sowohl zur Ernährung als auch zu den Ausgaben.
 *
 * Bewusst regelbasiert (keine KI nötig): läuft offline, sofort, kostenlos und
 * nachvollziehbar. Die KI im Chat-Tab ergänzt das für freie Rückfragen.
 *
 * Die Tipps richten sich nach dem im Einstellungs-Screen gewählten [NutritionGoal] –
 * so bekommt jemand mit Ziel "Weniger Zucker" andere Hinweise als jemand mit
 * "Günstiger & selbst kochen".
 */
class AdvisorUseCase @Inject constructor() {

    operator fun invoke(
        receipts: List<ReceiptWithItems>,
        goal: NutritionGoal,
        freshTargetPercent: Int
    ): NutritionScore {
        val allItems = receipts.flatMap { it.items }
        val foodItems = allItems.filter {
            it.nutritionTag != null && it.nutritionTag != NutritionTag.NICHT_LEBENSMITTEL
        }

        if (foodItems.isEmpty()) {
            return NutritionScore(
                score = 0,
                freshSharePercent = 0,
                processedSharePercent = 0,
                fastFoodSharePercent = 0,
                tips = listOf(
                    HealthTip(
                        kind = TipKind.INFO,
                        title = "Noch keine Daten",
                        detail = "Scanne ein paar Kassenzettel, dann werte ich deine Einkäufe " +
                            "hier aus und zeige dir, was du konkret verbessern kannst."
                    )
                ),
                hasData = false
            )
        }

        // --- Score ---
        val totalWeight = foodItems.sumOf { it.nutritionTag!!.scoreWeight }
        val maxPossible = foodItems.size * 10
        val score = (((totalWeight.toDouble() / maxPossible) + 1) / 2 * 100)
            .coerceIn(0.0, 100.0).toInt()

        fun share(predicate: (NutritionTag) -> Boolean) =
            (foodItems.count { predicate(it.nutritionTag!!) } * 100.0 / foodItems.size).toInt()

        val freshShare = share { it == NutritionTag.FRISCH_UNVERARBEITET || it == NutritionTag.VOLLWERTIG }
        val processedShare = share { it == NutritionTag.VERARBEITET }
        val fastFoodShare = share { it == NutritionTag.STARK_VERARBEITET }

        return NutritionScore(
            score = score,
            freshSharePercent = freshShare,
            processedSharePercent = processedShare,
            fastFoodSharePercent = fastFoodShare,
            tips = buildTips(receipts, allItems, foodItems, goal, freshShare, freshTargetPercent),
            hasData = true
        )
    }

    private fun buildTips(
        receipts: List<ReceiptWithItems>,
        allItems: List<com.bonsai.app.data.model.ReceiptItem>,
        foodItems: List<com.bonsai.app.data.model.ReceiptItem>,
        goal: NutritionGoal,
        freshShare: Int,
        freshTarget: Int
    ): List<HealthTip> {
        val tips = mutableListOf<HealthTip>()

        // --- 1. Wiederholt gekaufte stark verarbeitete Produkte ---
        foodItems
            .filter { it.nutritionTag == NutritionTag.STARK_VERARBEITET }
            .groupBy { it.name.trim().lowercase(Locale.GERMANY) }
            .filter { it.value.size >= 3 }
            .toList()
            .sortedByDescending { it.second.size }
            .take(2)
            .forEach { (_, items) ->
                val displayName = items.first().name
                val spent = items.sumOf { it.priceEuro * it.quantity }
                tips += HealthTip(
                    kind = TipKind.ERNAEHRUNG,
                    title = "$displayName: ${items.size}× gekauft",
                    detail = "Dafür sind %.2f € zusammengekommen. %s".format(
                        Locale.GERMANY, spent, alternativeFor(displayName)
                    ),
                    relatedItemNames = listOf(displayName)
                )
            }

        // --- 2. Zielabhängige Hinweise ---
        when (goal) {
            NutritionGoal.WENIGER_ZUCKER -> {
                val sugary = foodItems.filter { it.nutritionTag == NutritionTag.ZUCKERHALTIG }
                if (sugary.size >= 2) {
                    val spent = sugary.sumOf { it.priceEuro * it.quantity }
                    tips += HealthTip(
                        kind = TipKind.ERNAEHRUNG,
                        title = "${sugary.size} zuckerhaltige Produkte",
                        detail = "Zusammen %.2f €. Wasser, ungesüßter Tee oder Mineralwasser mit " +
                            "Zitrone ersetzen die meisten Softdrinks fast unbemerkt – und sparen Geld."
                            .format(Locale.GERMANY, spent)
                    )
                }
            }

            NutritionGoal.MEHR_PFLANZLICH -> {
                val fresh = foodItems.count {
                    it.nutritionTag == NutritionTag.FRISCH_UNVERARBEITET
                }
                tips += HealthTip(
                    kind = TipKind.ERNAEHRUNG,
                    title = "Frischanteil: $freshShare %",
                    detail = if (freshShare >= freshTarget)
                        "Du liegst über deinem Ziel von $freshTarget %. $fresh frische Posten im Korb – gut so."
                    else
                        "Dein Ziel sind $freshTarget %. Ein Griff mehr zu Gemüse, Obst oder Hülsenfrüchten " +
                            "pro Einkauf würde reichen, um dort hinzukommen."
                )
            }

            NutritionGoal.GUENSTIGER_KOCHEN -> {
                val convenience = allItems.filter {
                    it.category == Category.SNACKS_UNTERWEGS ||
                        it.category == Category.RESTAURANT_LIEFERDIENST
                }
                if (convenience.isNotEmpty()) {
                    val spent = convenience.sumOf { it.priceEuro * it.quantity }
                    tips += HealthTip(
                        kind = TipKind.AUSGABEN,
                        title = "Unterwegs & Lieferdienst: %.2f €".format(Locale.GERMANY, spent),
                        detail = "${convenience.size} Posten. Selbst gekocht liegt eine vergleichbare " +
                            "Mahlzeit meist bei 2–4 €. Schon zwei Mittagessen pro Woche selbst " +
                            "mitzunehmen macht hier den größten Unterschied."
                    )
                }
            }

            NutritionGoal.AUSGEWOGEN -> {
                tips += HealthTip(
                    kind = TipKind.ERNAEHRUNG,
                    title = "Frischanteil: $freshShare %",
                    detail = if (freshShare >= freshTarget)
                        "Über deinem Ziel von $freshTarget %. Weiter so."
                    else
                        "Dein Ziel sind $freshTarget %. Der einfachste Hebel: eine verarbeitete " +
                            "Position pro Einkauf durch eine frische ersetzen."
                )
            }

            NutritionGoal.KEIN_ZIEL -> Unit
        }

        // --- 3. Ausgaben-Hinweis: teuerste Kategorie ---
        val byCategory = allItems
            .groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.priceEuro * it.quantity } }
        byCategory.maxByOrNull { it.value }?.let { (category, sum) ->
            val total = byCategory.values.sum()
            if (total > 0) {
                val pct = (sum * 100 / total).toInt()
                tips += HealthTip(
                    kind = TipKind.AUSGABEN,
                    title = "Größter Posten: ${category.displayName}",
                    detail = "%.2f € – das sind %d %% deiner Ausgaben im Zeitraum.".format(
                        Locale.GERMANY, sum, pct
                    )
                )
            }
        }

        // --- 4. Teuerstes Geschäft ---
        if (receipts.size >= 3) {
            receipts.groupBy { it.receipt.merchant.trim() }
                .mapValues { (_, list) -> list.sumOf { it.receipt.totalEuro } }
                .maxByOrNull { it.value }
                ?.let { (merchant, sum) ->
                    if (merchant.isNotBlank()) {
                        tips += HealthTip(
                            kind = TipKind.AUSGABEN,
                            title = "Meiste Ausgaben bei $merchant",
                            detail = "%.2f € im gewählten Zeitraum.".format(Locale.GERMANY, sum)
                        )
                    }
                }
        }

        if (tips.isEmpty()) {
            tips += HealthTip(
                kind = TipKind.INFO,
                title = "Alles im Rahmen",
                detail = "In diesem Zeitraum sehe ich keine Auffälligkeiten. " +
                    "Scanne weiter Belege, dann werden die Hinweise genauer."
            )
        }

        return tips
    }

    /** Kleine, konkrete Ersatzvorschläge für häufige Fertigprodukte. */
    private fun alternativeFor(productName: String): String {
        val n = productName.lowercase(Locale.GERMANY)
        return when {
            n.contains("semmel") || n.contains("weckerl") || n.contains("brötchen") ->
                "Eine Vollkornvariante mit magerem Belag hält deutlich länger satt."
            n.contains("cola") || n.contains("limo") || n.contains("energy") ->
                "Mineralwasser mit Zitrone oder ungesüßter Eistee sind der einfachste Tausch."
            n.contains("chips") || n.contains("snack") ->
                "Nüsse oder Studentenfutter liefern bei ähnlichem Griff deutlich mehr Nährwert."
            n.contains("pizza") || n.contains("fertig") ->
                "Eine selbst belegte Basis dauert kaum länger und kostet meist die Hälfte."
            n.contains("wurst") || n.contains("leberkäse") || n.contains("schinken") ->
                "Ein-, zweimal pro Woche durch Hummus, Käse oder Ei ersetzen macht schon viel aus."
            else ->
                "Überlege, ob sich hier eine frischere oder selbst zubereitete Variante anbietet."
        }
    }
}
