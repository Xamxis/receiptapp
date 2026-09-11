package com.bonsai.app.data.model

/**
 * Alle vom Nutzer einstellbaren Werte an einem Ort.
 * Persistiert via DataStore (siehe SettingsRepository).
 */
data class UserSettings(
    // --- Budget ---
    val monthlyBudgetEuro: Double? = null,
    val weeklySnackBudgetEuro: Double? = null,
    val budgetAlertsEnabled: Boolean = true,

    // --- Ernährungsziel ---
    val nutritionGoal: NutritionGoal = NutritionGoal.AUSGEWOGEN,
    /** Zielwert für den Anteil frischer/unverarbeiteter Lebensmittel (in %). */
    val freshFoodTargetPercent: Int = 50,

    // --- Darstellung ---
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You: Farben aus dem Hintergrundbild übernehmen (ab Android 12). */
    val useDynamicColor: Boolean = false,

    // --- Datenschutz ---
    val aiAnalysisEnabled: Boolean = true
)

/**
 * Ernährungsziel des Nutzers. Steuert, worauf der Berater seine Tipps ausrichtet –
 * ersetzt die vorherige unklare Einzeltoggle-Lösung.
 */
enum class NutritionGoal(val displayName: String, val description: String) {
    AUSGEWOGEN(
        "Ausgewogen essen",
        "Möglichst viel Frisches, weniger stark Verarbeitetes"
    ),
    WENIGER_ZUCKER(
        "Weniger Zucker",
        "Fokus auf zuckerarme Alternativen bei Getränken und Snacks"
    ),
    MEHR_PFLANZLICH(
        "Mehr pflanzlich",
        "Mehr Gemüse, Obst und Hülsenfrüchte im Einkaufskorb"
    ),
    GUENSTIGER_KOCHEN(
        "Günstiger & selbst kochen",
        "Weniger Fertigprodukte und Unterwegs-Verpflegung, mehr Grundzutaten"
    ),
    KEIN_ZIEL(
        "Kein spezielles Ziel",
        "Nur Auswertung, keine Ernährungstipps"
    )
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("Systemeinstellung folgen"),
    HELL("Immer hell"),
    DUNKEL("Immer dunkel")
}
