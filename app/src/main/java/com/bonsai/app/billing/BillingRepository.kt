package com.bonsai.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Zustand der Play-Verbindung und des Kaufs. */
data class BillingState(
    val isPro: Boolean = false,
    val priceText: String? = null,
    val isConnected: Boolean = false,
    val isPurchasing: Boolean = false,
    val error: String? = null
)

/**
 * Kapselt Google Play Billing für die einmalige Pro-Freischaltung.
 *
 * WICHTIG für den Test: Billing funktioniert nur, wenn die App über Google Play
 * installiert wurde (mindestens interner Test-Track) und das Produkt in der Play
 * Console angelegt ist. In Emulatoren, Browser-Testdiensten oder per ADB
 * installierten Debug-Builds meldet Play "Produkt nicht gefunden" – das ist
 * erwartetes Verhalten. Zum Testen gibt es in Debug-Builds einen Schalter
 * in den Einstellungen.
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proStatusStore: ProStatusStore
) : PurchasesUpdatedListener {

    companion object {
        /** Produkt-ID, die exakt so in der Google Play Console angelegt werden muss. */
        const val PRO_PRODUCT_ID = "bonsai_pro_lifetime"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init {
        // Zuletzt bekannter Status aus dem lokalen Speicher, damit Pro-Funktionen
        // auch offline sofort verfügbar sind und nicht erst nach Play-Verbindung.
        scope.launch {
            proStatusStore.isPro.collect { cached ->
                _state.value = _state.value.copy(isPro = cached)
            }
        }
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(isConnected = true, error = null)
                    scope.launch {
                        loadProductDetails()
                        restorePurchases()
                    }
                } else {
                    _state.value = _state.value.copy(
                        isConnected = false,
                        error = "Google Play nicht verfügbar (${result.debugMessage.ifBlank { result.responseCode.toString() }})"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(isConnected = false)
            }
        })
    }

    private suspend fun loadProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull()

        if (details != null) {
            productDetails = details
            _state.value = _state.value.copy(
                // Play liefert den lokalisierten Preis inkl. Währung des Nutzers
                priceText = details.oneTimePurchaseOfferDetails?.formattedPrice,
                error = null
            )
        } else {
            _state.value = _state.value.copy(
                error = "Produkt konnte nicht geladen werden. " +
                    "Das ist normal, solange die App nicht über Google Play installiert ist."
            )
        }
    }

    /** Startet den Kauf-Dialog. Muss aus einer Activity heraus aufgerufen werden. */
    fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (details == null) {
            _state.value = _state.value.copy(
                error = "Der Kauf ist gerade nicht möglich. Prüfe deine Verbindung zu Google Play."
            )
            return
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()

        _state.value = _state.value.copy(isPurchasing = true, error = null)
        billingClient.launchBillingFlow(activity, flowParams)
    }

    /** Holt bereits getätigte Käufe – z. B. nach Neuinstallation oder Gerätewechsel. */
    suspend fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val result = billingClient.queryPurchasesAsync(params)
        val active = result.purchasesList.any { purchase ->
            purchase.products.contains(PRO_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        result.purchasesList.forEach { handlePurchase(it) }

        if (active) {
            proStatusStore.setPro(true)
        }
        _state.value = _state.value.copy(isPurchasing = false)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    scope.launch { handlePurchase(purchase) }
                }
                _state.value = _state.value.copy(isPurchasing = false)
            }

            BillingClient.BillingResponseCode.USER_CANCELED ->
                _state.value = _state.value.copy(isPurchasing = false, error = null)

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch { restorePurchases() }
            }

            else -> _state.value = _state.value.copy(
                isPurchasing = false,
                error = "Kauf fehlgeschlagen (${result.debugMessage.ifBlank { result.responseCode.toString() }})"
            )
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRO_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Nicht bestätigte Käufe werden von Google nach 3 Tagen automatisch erstattet.
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params)
        }

        proStatusStore.setPro(true)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
