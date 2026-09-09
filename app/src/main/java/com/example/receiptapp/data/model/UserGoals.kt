package com.example.receiptapp.data.model

/**
 * Individuelle Budget- und Ernährungsziele des Nutzers.
 * Persistiert via DataStore (siehe UserGoalsRepository) statt Room,
 * da es sich um ein einzelnes Settings-Objekt handelt, kein Listen-Datensatz.
 */
data class UserGoals(
    val weeklySnackBudgetEuro: Double = 50.0,
    val monthlyTotalBudgetEuro: Double? = null,
    val reduceProcessedMeat: Boolean = false,
    val maxProcessedFoodSharePercent: Int = 40,
    val budgetAlertsEnabled: Boolean = true
)

/** Ergebnis der Zielüberprüfung, für UI-Badges/Warnungen im Dashboard */
data class GoalStatus(
    val goalLabel: String,
    val currentValue: Double,
    val targetValue: Double,
    val isExceeded: Boolean
)
