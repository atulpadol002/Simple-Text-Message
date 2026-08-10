package com.ap.messages.premium

import android.app.Activity
import android.content.Context
import com.ap.messages.ads.AdRuntime
import com.ap.messages.ads.FullScreenAdCoordinator
import com.ap.messages.ads.FullScreenAdType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PremiumEntitlementStatus {
    CHECKING,
    ACTIVE,
    INACTIVE,
    PENDING,
    ERROR
}

data class PremiumPlanUi(
    val plan: PremiumPlan,
    val formattedPrice: String? = null,
    val available: Boolean = false
)

data class PremiumBillingState(
    val entitlementStatus: PremiumEntitlementStatus = PremiumEntitlementStatus.CHECKING,
    val monthly: PremiumPlanUi = PremiumPlanUi(PremiumPlan.MONTHLY),
    val yearly: PremiumPlanUi = PremiumPlanUi(PremiumPlan.YEARLY),
    val billingReady: Boolean = false,
    val purchaseInProgress: Boolean = false,
    val message: String? = null
) {
    val isPremium: Boolean
        get() = entitlementStatus == PremiumEntitlementStatus.ACTIVE
}

/**
 * Process-wide BillingClient owner. Entitlement is derived only from current Google Play purchases;
 * no purchased flag is persisted locally.
 */
object PremiumBillingManager : PurchasesUpdatedListener {
    private data class PurchaseOption(
        val productDetails: ProductDetails,
        val offerDetails: ProductDetails.SubscriptionOfferDetails
    )

    private val _state = MutableStateFlow(PremiumBillingState())
    val state: StateFlow<PremiumBillingState> = _state.asStateFlow()

    private val _adsAllowed = MutableStateFlow(false)
    val adsAllowed: StateFlow<Boolean> = _adsAllowed.asStateFlow()

    private var initialized = false
    private var connecting = false
    private lateinit var billingClient: BillingClient
    private val connectionWaiters = mutableListOf<(Boolean) -> Unit>()
    private val purchaseOptions = mutableMapOf<PremiumPlan, PurchaseOption>()

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()
        withReady { ready ->
            if (ready) {
                queryProductDetails()
                queryPurchases()
            }
        }
    }

    fun refreshPurchases(onComplete: () -> Unit = {}) {
        setEntitlement(PremiumEntitlementStatus.CHECKING, null)
        withReady { ready ->
            if (!ready) {
                setEntitlement(PremiumEntitlementStatus.ERROR, "Google Play Billing is unavailable.")
                onComplete()
            } else {
                queryProductDetails()
                queryPurchases(onComplete)
            }
        }
    }

    fun restorePurchases() {
        refreshPurchases {
            val message = when (_state.value.entitlementStatus) {
                PremiumEntitlementStatus.ACTIVE -> "Purchase restored."
                PremiumEntitlementStatus.PENDING -> "Purchase is pending."
                PremiumEntitlementStatus.INACTIVE -> "No active subscription was found."
                PremiumEntitlementStatus.ERROR -> _state.value.message
                PremiumEntitlementStatus.CHECKING -> null
            }
            _state.value = _state.value.copy(message = message)
        }
    }

    fun refreshProductDetails() {
        withReady { ready ->
            if (ready) queryProductDetails()
            else _state.value = _state.value.copy(message = "Google Play Billing is unavailable.")
        }
    }

    fun launchPurchase(activity: Activity, plan: PremiumPlan) {
        if (activity.isFinishing || activity.isDestroyed) return
        val option = purchaseOptions[plan]
        if (option == null) {
            _state.value = _state.value.copy(
                message = "${plan.displayName} is not available from Google Play."
            )
            refreshProductDetails()
            return
        }
        if (!billingClient.isReady) {
            _state.value = _state.value.copy(message = "Google Play Billing is unavailable.")
            return
        }
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.BILLING)) {
            _state.value = _state.value.copy(message = "Another full-screen action is in progress.")
            return
        }

        AdRuntime.suppressNextAppOpen()
        _state.value = _state.value.copy(purchaseInProgress = true, message = null)
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(option.productDetails)
            .setOfferToken(option.offerDetails.offerToken)
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            FullScreenAdCoordinator.release(FullScreenAdType.BILLING)
            _state.value = _state.value.copy(purchaseInProgress = false)
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                refreshPurchases()
            } else {
                setEntitlement(
                    PremiumEntitlementStatus.ERROR,
                    billingErrorMessage("Unable to start purchase", result)
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        FullScreenAdCoordinator.release(FullScreenAdType.BILLING)
        _state.value = _state.value.copy(purchaseInProgress = false)
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> refreshPurchases()
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            else -> setEntitlement(
                PremiumEntitlementStatus.ERROR,
                billingErrorMessage("Purchase failed", result)
            )
        }
    }

    private fun queryPurchases(onComplete: () -> Unit = {}) {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            } else {
                setEntitlement(
                    PremiumEntitlementStatus.ERROR,
                    billingErrorMessage("Unable to restore purchases", result)
                )
            }
            onComplete()
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val relevant = purchases.filter { purchase ->
            PremiumConfig.SUBSCRIPTION_PRODUCT_ID in purchase.products
        }
        relevant
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach(::acknowledge)

        val activePurchase = relevant.firstOrNull { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val pendingPurchase = relevant.firstOrNull { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PENDING
        }
        when {
            activePurchase != null -> setEntitlement(PremiumEntitlementStatus.ACTIVE, null)
            pendingPurchase != null -> setEntitlement(
                PremiumEntitlementStatus.PENDING,
                "Your purchase is pending. No Ads will activate after Google Play completes it."
            )
            else -> setEntitlement(PremiumEntitlementStatus.INACTIVE, null)
        }
    }

    private fun acknowledge(purchase: Purchase) {
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.value = _state.value.copy(
                    message = billingErrorMessage("Purchase acknowledgement failed", result)
                )
            }
        }
    }

    private fun queryProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PremiumConfig.SUBSCRIPTION_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build()
        ) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                purchaseOptions.clear()
                updatePlanUi(message = billingErrorMessage("Unable to load prices", result))
                return@queryProductDetailsAsync
            }
            val details = queryResult.productDetailsList.firstOrNull { item ->
                item.productId == PremiumConfig.SUBSCRIPTION_PRODUCT_ID
            }
            purchaseOptions.clear()
            if (details != null) {
                PremiumPlan.entries.forEach { plan ->
                    val offer = details.subscriptionOfferDetails.orEmpty().firstOrNull { candidate ->
                        candidate.basePlanId == plan.basePlanId && candidate.offerId == null &&
                            candidate.pricingPhases.pricingPhaseList.any { phase ->
                                phase.billingPeriod == plan.billingPeriod
                            }
                    }
                    if (offer != null) purchaseOptions[plan] = PurchaseOption(details, offer)
                }
            }
            val missing = PremiumPlan.entries.filterNot(purchaseOptions::containsKey)
            updatePlanUi(
                message = if (missing.isEmpty()) null else {
                    "Google Play is missing: ${missing.joinToString { it.displayName }}."
                }
            )
        }
    }

    private fun updatePlanUi(message: String?) {
        fun ui(plan: PremiumPlan): PremiumPlanUi {
            val option = purchaseOptions[plan]
            val price = option?.offerDetails?.pricingPhases?.pricingPhaseList
                ?.lastOrNull { it.billingPeriod == plan.billingPeriod }
                ?.formattedPrice
            return PremiumPlanUi(plan = plan, formattedPrice = price, available = price != null)
        }
        _state.value = _state.value.copy(
            monthly = ui(PremiumPlan.MONTHLY),
            yearly = ui(PremiumPlan.YEARLY),
            message = message ?: _state.value.message
        )
    }

    private fun setEntitlement(status: PremiumEntitlementStatus, message: String?) {
        _adsAllowed.value = status != PremiumEntitlementStatus.ACTIVE &&
            status != PremiumEntitlementStatus.CHECKING
        _state.value = _state.value.copy(entitlementStatus = status, message = message)
    }

    private fun withReady(block: (Boolean) -> Unit) {
        if (!initialized) {
            block(false)
            return
        }
        if (billingClient.isReady) {
            block(true)
            return
        }
        synchronized(this) {
            connectionWaiters += block
            if (connecting) return
            connecting = true
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ready = result.responseCode == BillingClient.BillingResponseCode.OK
                _state.value = _state.value.copy(billingReady = ready)
                if (!ready) {
                    setEntitlement(
                        PremiumEntitlementStatus.ERROR,
                        billingErrorMessage("Google Play Billing setup failed", result)
                    )
                }
                val waiters = synchronized(this@PremiumBillingManager) {
                    connecting = false
                    connectionWaiters.toList().also { connectionWaiters.clear() }
                }
                waiters.forEach { it(ready) }
            }

            override fun onBillingServiceDisconnected() {
                synchronized(this@PremiumBillingManager) { connecting = false }
                _state.value = _state.value.copy(billingReady = false)
                setEntitlement(
                    PremiumEntitlementStatus.ERROR,
                    "Google Play Billing disconnected."
                )
            }
        })
    }

    private fun billingErrorMessage(prefix: String, result: BillingResult): String =
        "$prefix (${result.responseCode}): ${result.debugMessage}"
}
