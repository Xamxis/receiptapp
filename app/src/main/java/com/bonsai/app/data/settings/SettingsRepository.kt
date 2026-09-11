package com.bonsai.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bonsai.app.data.model.NutritionGoal
import com.bonsai.app.data.model.ThemeMode
import com.bonsai.app.data.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "bonsai_settings")

/** Persistiert alle Nutzereinstellungen via DataStore. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val MONTHLY_BUDGET = doublePreferencesKey("monthly_budget")
        val SNACK_BUDGET = doublePreferencesKey("snack_budget")
        val ALERTS = booleanPreferencesKey("budget_alerts")
        val NUTRITION_GOAL = stringPreferencesKey("nutrition_goal")
        val FRESH_TARGET = intPreferencesKey("fresh_target")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            monthlyBudgetEuro = p[Keys.MONTHLY_BUDGET],
            weeklySnackBudgetEuro = p[Keys.SNACK_BUDGET],
            budgetAlertsEnabled = p[Keys.ALERTS] ?: true,
            nutritionGoal = p[Keys.NUTRITION_GOAL]
                ?.let { name -> NutritionGoal.entries.find { it.name == name } }
                ?: NutritionGoal.AUSGEWOGEN,
            freshFoodTargetPercent = p[Keys.FRESH_TARGET] ?: 50,
            themeMode = p[Keys.THEME_MODE]
                ?.let { name -> ThemeMode.entries.find { it.name == name } }
                ?: ThemeMode.SYSTEM,
            useDynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
            aiAnalysisEnabled = p[Keys.AI_ENABLED] ?: true
        )
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { p ->
            val current = UserSettings(
                monthlyBudgetEuro = p[Keys.MONTHLY_BUDGET],
                weeklySnackBudgetEuro = p[Keys.SNACK_BUDGET],
                budgetAlertsEnabled = p[Keys.ALERTS] ?: true,
                nutritionGoal = p[Keys.NUTRITION_GOAL]
                    ?.let { name -> NutritionGoal.entries.find { it.name == name } }
                    ?: NutritionGoal.AUSGEWOGEN,
                freshFoodTargetPercent = p[Keys.FRESH_TARGET] ?: 50,
                themeMode = p[Keys.THEME_MODE]
                    ?.let { name -> ThemeMode.entries.find { it.name == name } }
                    ?: ThemeMode.SYSTEM,
                useDynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
                aiAnalysisEnabled = p[Keys.AI_ENABLED] ?: true
            )
            val new = transform(current)

            new.monthlyBudgetEuro?.let { p[Keys.MONTHLY_BUDGET] = it } ?: p.remove(Keys.MONTHLY_BUDGET)
            new.weeklySnackBudgetEuro?.let { p[Keys.SNACK_BUDGET] = it } ?: p.remove(Keys.SNACK_BUDGET)
            p[Keys.ALERTS] = new.budgetAlertsEnabled
            p[Keys.NUTRITION_GOAL] = new.nutritionGoal.name
            p[Keys.FRESH_TARGET] = new.freshFoodTargetPercent
            p[Keys.THEME_MODE] = new.themeMode.name
            p[Keys.DYNAMIC_COLOR] = new.useDynamicColor
            p[Keys.AI_ENABLED] = new.aiAnalysisEnabled
        }
    }
}
