package com.bonsai.app.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bonsai.app.data.model.Category
import com.bonsai.app.data.model.GoalStatus
import com.bonsai.app.data.model.MerchantSummary
import com.bonsai.app.data.model.NutritionScore
import com.bonsai.app.data.model.TimeRange
import com.bonsai.app.data.repository.ReceiptRepository
import com.bonsai.app.data.settings.SettingsRepository
import com.bonsai.app.domain.AdvisorUseCase
import com.bonsai.app.domain.AggregateMerchantsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CategorySpend(val category: Category, val totalEuro: Double, val sharePercent: Int)

data class OverviewUiState(
    val range: TimeRange = TimeRange.DIESER_MONAT,
    val total: Double = 0.0,
    val receiptCount: Int = 0,
    val byCategory: List<CategorySpend> = emptyList(),
    val topMerchants: List<MerchantSummary> = emptyList(),
    val score: NutritionScore? = null,
    val budgetStatus: GoalStatus? = null,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val repository: ReceiptRepository,
    settingsRepository: SettingsRepository,
    private val advisor: AdvisorUseCase,
    private val aggregateMerchants: AggregateMerchantsUseCase
) : ViewModel() {

    private val _range = MutableStateFlow(TimeRange.DIESER_MONAT)
    val range: StateFlow<TimeRange> = _range

    val uiState: StateFlow<OverviewUiState> =
        combine(
            _range.flatMapLatest { repository.observeRange(it) },
            settingsRepository.settings,
            _range
        ) { receipts, settings, range ->

            val items = receipts.flatMap { it.items }
            val total = receipts.sumOf { it.receipt.totalEuro }

            val categoryTotals = items
                .groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.priceEuro * it.quantity } }
            val categorySum = categoryTotals.values.sum()

            val byCategory = categoryTotals
                .map { (category, sum) ->
                    CategorySpend(
                        category = category,
                        totalEuro = sum,
                        sharePercent = if (categorySum > 0) (sum * 100 / categorySum).toInt() else 0
                    )
                }
                .sortedByDescending { it.totalEuro }

            // Monatsbudget nur beim Monats-Zeitraum sinnvoll anzeigen
            val budget = settings.monthlyBudgetEuro
                ?.takeIf { range == TimeRange.DIESER_MONAT && it > 0 }
                ?.let { GoalStatus("Monatsbudget", total, it) }

            OverviewUiState(
                range = range,
                total = total,
                receiptCount = receipts.size,
                byCategory = byCategory,
                topMerchants = aggregateMerchants(receipts).take(4),
                score = advisor(receipts, settings.nutritionGoal, settings.freshFoodTargetPercent),
                budgetStatus = budget,
                isLoading = false
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    fun setRange(range: TimeRange) {
        _range.value = range
    }
}
