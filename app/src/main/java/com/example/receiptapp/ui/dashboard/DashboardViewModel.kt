package com.example.receiptapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.receiptapp.data.model.Category
import com.example.receiptapp.data.model.NutritionScore
import com.example.receiptapp.data.repository.ReceiptRepository
import com.example.receiptapp.domain.CalculateNutritionScoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

data class CategorySpend(val category: Category, val totalEuro: Double)

data class FinanceUiState(
    val totalThisMonth: Double = 0.0,
    val spendByCategory: List<CategorySpend> = emptyList(),
    val isLoading: Boolean = true
)

data class HealthUiState(
    val score: NutritionScore? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: ReceiptRepository,
    calculateNutritionScore: CalculateNutritionScoreUseCase
) : ViewModel() {

    private val monthStart = LocalDate.now().withDayOfMonth(1)
    private val monthEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())

    private val weekFields = WeekFields.of(Locale.GERMANY)
    private val weekStart = LocalDate.now().with(weekFields.dayOfWeek(), 1)
    private val weekEnd = weekStart.plusDays(6)

    val financeState: StateFlow<FinanceUiState> = repository
        .observeReceiptsInRange(monthStart, monthEnd)
        .map { receipts ->
            val byCategory = receipts
                .flatMap { it.items }
                .groupBy { it.category }
                .map { (category, items) -> CategorySpend(category, items.sumOf { it.priceEuro * it.quantity }) }
                .sortedByDescending { it.totalEuro }

            FinanceUiState(
                totalThisMonth = receipts.sumOf { it.receipt.totalEuro },
                spendByCategory = byCategory,
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceUiState())

    val healthState: StateFlow<HealthUiState> = repository
        .observeReceiptsInRange(weekStart, weekEnd)
        .map { receipts -> HealthUiState(score = calculateNutritionScore(receipts), isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HealthUiState())
}
