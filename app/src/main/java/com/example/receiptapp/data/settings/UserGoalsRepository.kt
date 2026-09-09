package com.example.receiptapp.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.receiptapp.data.model.UserGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_goals")

/** Persistiert Ernährungs-/Budgetziele via DataStore (kein Room nötig – Singleton-Objekt). */
@Singleton
class UserGoalsRepository @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val WEEKLY_SNACK_BUDGET = doublePreferencesKey("weekly_snack_budget")
        val MONTHLY_TOTAL_BUDGET = doublePreferencesKey("monthly_total_budget")
        val REDUCE_PROCESSED_MEAT = booleanPreferencesKey("reduce_processed_meat")
        val MAX_PROCESSED_SHARE = intPreferencesKey("max_processed_share")
        val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
    }

    val goalsFlow: Flow<UserGoals> = context.dataStore.data.map { prefs ->
        UserGoals(
            weeklySnackBudgetEuro = prefs[Keys.WEEKLY_SNACK_BUDGET] ?: 50.0,
            monthlyTotalBudgetEuro = prefs[Keys.MONTHLY_TOTAL_BUDGET],
            reduceProcessedMeat = prefs[Keys.REDUCE_PROCESSED_MEAT] ?: false,
            maxProcessedFoodSharePercent = prefs[Keys.MAX_PROCESSED_SHARE] ?: 40,
            budgetAlertsEnabled = prefs[Keys.ALERTS_ENABLED] ?: true
        )
    }

    suspend fun updateGoals(goals: UserGoals) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEEKLY_SNACK_BUDGET] = goals.weeklySnackBudgetEuro
            goals.monthlyTotalBudgetEuro?.let { prefs[Keys.MONTHLY_TOTAL_BUDGET] = it }
            prefs[Keys.REDUCE_PROCESSED_MEAT] = goals.reduceProcessedMeat
            prefs[Keys.MAX_PROCESSED_SHARE] = goals.maxProcessedFoodSharePercent
            prefs[Keys.ALERTS_ENABLED] = goals.budgetAlertsEnabled
        }
    }
}
