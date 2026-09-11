package com.bonsai.app.data.model

/**
 * Sortier- und Gruppieroptionen für die Beleg-Liste.
 * Wird im UI als Chip-Reihe angeboten.
 */
enum class ReceiptSort(val displayName: String) {
    DATUM_NEU("Neueste zuerst"),
    DATUM_ALT("Älteste zuerst"),
    BETRAG_HOCH("Teuerste zuerst"),
    BETRAG_NIEDRIG("Günstigste zuerst"),
    HAENDLER_AZ("Händler A–Z")
}

/** Gruppierung der Beleg-Liste. */
enum class ReceiptGrouping(val displayName: String) {
    KEINE("Alle Belege"),
    NACH_HAENDLER("Nach Geschäft"),
    NACH_MONAT("Nach Monat")
}

/** Sortierung der Produkt-Übersicht. */
enum class ProductSort(val displayName: String) {
    HAEUFIGKEIT("Am häufigsten"),
    AUSGABEN("Höchste Ausgaben"),
    NAME_AZ("Name A–Z"),
    ZULETZT("Zuletzt gekauft")
}

/**
 * Aggregiertes Produkt über alle Belege hinweg: "Wurstsemmel, 7x gekauft,
 * 24,50 € gesamt, zuletzt bei Spar".
 */
data class ProductSummary(
    val name: String,
    val purchaseCount: Int,
    val totalSpentEuro: Double,
    val averagePriceEuro: Double,
    val category: Category,
    val nutritionTag: NutritionTag?,
    val lastMerchant: String,
    val lastPurchaseIso: String
)

/** Ausgaben eines Geschäfts über den gewählten Zeitraum. */
data class MerchantSummary(
    val merchant: String,
    val receiptCount: Int,
    val totalSpentEuro: Double
)

/** Zeitraum-Filter für Übersicht und Listen. */
enum class TimeRange(val displayName: String) {
    DIESE_WOCHE("Diese Woche"),
    DIESER_MONAT("Dieser Monat"),
    LETZTE_3_MONATE("Letzte 3 Monate"),
    ALLES("Gesamter Zeitraum")
}

/** Ergebnis einer Budget-Zielprüfung, für Fortschrittsbalken im UI. */
data class GoalStatus(
    val label: String,
    val currentEuro: Double,
    val targetEuro: Double
) {
    val isExceeded: Boolean get() = currentEuro > targetEuro
    val progress: Float get() = if (targetEuro <= 0) 0f else (currentEuro / targetEuro).toFloat().coerceIn(0f, 1f)
}
