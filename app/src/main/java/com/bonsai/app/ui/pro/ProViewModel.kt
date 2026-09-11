package com.bonsai.app.ui.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bonsai.app.billing.BillingRepository
import com.bonsai.app.billing.BillingState
import com.bonsai.app.billing.FreeTier
import com.bonsai.app.billing.ProStatusStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Was die UI über Pro-Status und verbrauchtes Kontingent wissen muss. */
data class ProAccess(
    val isPro: Boolean = false,
    val scansUsed: Int = 0,
    val scanLimit: Int = FreeTier.MONTHLY_SCAN_LIMIT
) {
    val scansLeft: Int get() = (scanLimit - scansUsed).coerceAtLeast(0)
    val canScan: Boolean get() = isPro || scansLeft > 0
}

@HiltViewModel
class ProViewModel @Inject constructor(
    private val billingRepository: BillingRepository,
    proStatusStore: ProStatusStore
) : ViewModel() {

    val state: StateFlow<BillingState> = billingRepository.state

    val access: StateFlow<ProAccess> =
        combine(proStatusStore.isPro, proStatusStore.scansThisMonth) { isPro, scans ->
            ProAccess(isPro = isPro, scansUsed = scans)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProAccess())

    fun purchase(activity: Activity) = billingRepository.launchPurchase(activity)

    fun restore() {
        viewModelScope.launch { billingRepository.restorePurchases() }
    }

    fun clearError() = billingRepository.clearError()
}
