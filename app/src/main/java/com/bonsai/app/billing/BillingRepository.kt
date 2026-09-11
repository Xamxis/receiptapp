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

/** Zustand der Play-Verbindung und des Abos. */
data class BillingState(
    val isPro: Boolean = false,
    val priceText: String? = null,
    val billingPeriodText: String? = null,
    val isConnected: Boolean = false,
    val isPurchasing: Boolean = false,
    val error: String? = null
)

/**
 * Kapselt Google Play Billing für das monatliche Bonsai-Pro-Abo.
 *
 * WICHTIG für den Test: Billing funktioniert nur, wenn die App über Google Play
 * installiert wurde (mindestens interner Test-Track) und das Abo in der Play
 * Console angelegt UND aktiviert ist (inkl. Basisplan mit Preis). In Emulatoren,
 * Browser-Testdiensten oder per ADB installierten Debug-Builds meldet Play
 * "Produkt nicht gefunden" – das ist erwartetes Verhalten. Zum Testen gibt es
 * in Debug-Builds einen Schalter in den Einstellungen.
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proStatusStore: ProStatusStore
) : PurchasesUpdatedListener {

    companion object {
        /**
         * Abo-ID, die exakt so in der Google Play Console angelegt werden muss
         * (Monetarisierung -> Abos -> Abo erstellen). Zusätzlich braucht das Abo
         * dort einen aktiven Basisplan (z. B. ID "monthly-autorenew") mit dem
         * Preis 2,99 € und automatischer Verlängerung.
         */
        const val PRO_PRODUCT_ID = "bonsai_pro_monthly"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null
    private var offerToken: String? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        // .enableOneTimeProducts() ist laut Play-Dokumentation IMMER Pflicht,
        // auch wenn die App ausschließlich Abos verkauft.
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
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull()
        // Ein Abo kann mehrere Angebote haben (z. B. Einführungspreis + regulärer
        // Basisplan). Für ein einfaches Ein-Preis-Abo nehmen wir das erste Angebot.
        val offer = details?.subscriptionOfferDetails?.firstOrNull()
        val pricingPhase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()

        if (details != null && offer != null) {
            productDetails = details
            offerToken = offer.offerToken
            _state.value = _state.value.copy(
                priceText = pricingPhase?.formattedPrice,
                billingPeriodText = describeBillingPeriod(pricingPhase?.billingPeriod),
                error = null
            )
        } else {
            _state.value = _state.value.copy(
                error = "Abo konnte nicht geladen werden. Das ist normal, solange die App " +
                    "nicht über Google Play installiert ist oder das Abo in der Play Console " +
                    "noch nicht aktiv ist."
            )
        }
    }

    private fun describeBillingPeriod(isoPeriod: String?): String = when (isoPeriod) {
        "P1M" -> "pro Monat"
        "P1Y" -> "pro Jahr"
        "P1W" -> "pro Woche"
        else -> ""
    }

    /** Startet den Kauf-Dialog. Muss aus einer Activity heraus aufgerufen werden. */
    fun launchPurchase(activity: Activity) {
        val details = productDetails
        val token = offerToken
        if (details == null || token == null) {
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
                        .setOfferToken(token)
                        .build()
                )
            )
            .build()

        _state.value = _state.value.copy(isPurchasing = true, error = null)
        billingClient.launchBillingFlow(activity, flowParams)
    }

    /**
     * Holt den aktuellen Abo-Status von Google Play – z. B. nach Neuinstallation,
     * Gerätewechsel oder um eine Kündigung/Ablauf des Abos zu erkennen.
     */
    suspend fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val result = billingClient.queryPurchasesAsync(params)
        val hasActiveSub = result.purchasesList.any { purchase ->
            purchase.products.contains(PRO_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        result.purchasesList.forEach { handlePurchase(it) }

        // Play liefert abgelaufene/gekündigte Abos hier nicht mehr als PURCHASED,
        // daher können wir den lokalen Status direkt auf das Abfrageergebnis setzen.
        proStatusStore.setPro(hasActiveSub)
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

        // Nicht bestätigte Käufe/Abo-Starts werden von Google nach 3 Tagen
        // automatisch storniert.
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
