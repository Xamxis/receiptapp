package com.example.receiptapp.domain

import com.example.receiptapp.data.model.Category
import com.example.receiptapp.data.model.GoalStatus
import com.example.receiptapp.data.model.ReceiptWithItems
import com.example.receiptapp.data.model.UserGoals
import javax.inject.Inject

/** Prüft aktuelle Ausgaben gegen die vom Nutzer gesetzten Ziele. */
class CheckBudgetGoalsUseCase @Inject constructor() {

    operator fun invoke(receiptsThisWeek: List<ReceiptWithItems>, goals: UserGoals): List<GoalStatus> {
        val snackSpend = receiptsThisWeek
            .flatMap { it.items }
            .filter { it.category == Category.SNACKS_UNTERWEGS }
            .sumOf { it.priceEuro * it.quantity }

        val statuses = mutableListOf(
            GoalStatus(
                goalLabel = "Snack-Budget diese Woche",
                currentValue = snackSpend,
                targetValue = goals.weeklySnackBudgetEuro,
                isExceeded = snackSpend > goals.weeklySnackBudgetEuro
            )
        )

        goals.monthlyTotalBudgetEuro?.let { monthlyBudget ->
            val monthSpend = receiptsThisWeek.sumOf { it.receipt.totalEuro } // Aufrufer übergibt hier ggf. Monatsdaten
            statuses += GoalStatus(
                goalLabel = "Monatsbudget gesamt",
                currentValue = monthSpend,
                targetValue = monthlyBudget,
                isExceeded = monthSpend > monthlyBudget
            )
        }

        return statuses
    }
}
