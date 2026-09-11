package com.bonsai.app.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bonsai.app.data.model.ProductSort
import com.bonsai.app.data.model.ProductSummary
import com.bonsai.app.data.model.ReceiptGrouping
import com.bonsai.app.data.model.ReceiptSort
import com.bonsai.app.data.model.TimeRange
import com.bonsai.app.data.repository.ReceiptRepository
import com.bonsai.app.domain.AggregateProductsUseCase
import com.bonsai.app.domain.ReceiptGroup
import com.bonsai.app.domain.SortReceiptsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Welcher Tab im Belege-Screen aktiv ist. */
enum class ReceiptsTab(val displayName: String) {
    BELEGE("Belege"),
    PRODUKTE("Produkte")
}

data class ReceiptsUiState(
    val tab: ReceiptsTab = ReceiptsTab.BELEGE,
    val range: TimeRange = TimeRange.ALLES,
    val query: String = "",
    val sort: ReceiptSort = ReceiptSort.DATUM_NEU,
    val grouping: ReceiptGrouping = ReceiptGrouping.KEINE,
    val productSort: ProductSort = ProductSort.HAEUFIGKEIT,
    val groups: List<ReceiptGroup> = emptyList(),
    val products: List<ProductSummary> = emptyList(),
    val totalReceipts: Int = 0,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    private val repository: ReceiptRepository,
    private val sortReceipts: SortReceiptsUseCase,
    private val aggregateProducts: AggregateProductsUseCase
) : ViewModel() {

    private val filters = MutableStateFlow(ReceiptsUiState())

    val uiState: StateFlow<ReceiptsUiState> =
        combine(
            filters,
            filters.flatMapLatest { repository.observeRange(it.range) }
        ) { f, receipts ->
            f.copy(
                groups = sortReceipts(receipts, f.sort, f.grouping, f.query),
                products = aggregateProducts(receipts, f.productSort, f.query),
                totalReceipts = receipts.size,
                isLoading = false
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptsUiState())

    fun setTab(tab: ReceiptsTab) { filters.value = filters.value.copy(tab = tab) }
    fun setRange(range: TimeRange) { filters.value = filters.value.copy(range = range) }
    fun setQuery(query: String) { filters.value = filters.value.copy(query = query) }
    fun setSort(sort: ReceiptSort) { filters.value = filters.value.copy(sort = sort) }
    fun setGrouping(grouping: ReceiptGrouping) { filters.value = filters.value.copy(grouping = grouping) }
    fun setProductSort(sort: ProductSort) { filters.value = filters.value.copy(productSort = sort) }

    fun deleteReceipt(id: Long) {
        viewModelScope.launch { repository.deleteReceipt(id) }
    }
}
