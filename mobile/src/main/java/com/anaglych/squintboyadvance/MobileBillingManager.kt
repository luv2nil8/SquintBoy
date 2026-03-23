package com.anaglych.squintboyadvance

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Single source of truth for Pro entitlement on the phone.
 *
 * Google Play is authoritative. SharedPreferences is a cache used only to
 * avoid a flash of wrong UI while billing connects.
 *
 * Pushes entitlement to watch on:
 *   - State change (purchase or revocation)
 *   - Billing client connect (covers app restart)
 *   - Entitlement request from watch
 * Retries failed pushes with exponential backoff.
 */
class MobileBillingManager private constructor(context: Context) {

    companion object {
        private const val TAG = "MobileBilling"
        private const val PREFS_NAME = "entitlement"
        private const val KEY_IS_PRO = "is_pro"
        private const val PRODUCT_ID = "squintboy_pro"
        private const val MAX_PUSH_RETRIES = 5

        @Volatile
        private var instance: MobileBillingManager? = null

        fun getInstance(context: Context): MobileBillingManager =
            instance ?: synchronized(this) {
                instance ?: MobileBillingManager(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_IS_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private var pushRetryJob: Job? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val validPurchases = purchases.orEmpty().filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (validPurchases.isNotEmpty()) {
                    validPurchases.forEach { acknowledgePurchase(it) }
                    setProState(true)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                Log.d(TAG, "User cancelled purchase")
            else ->
                Log.w(TAG, "Purchase update error: ${billingResult.debugMessage}")
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    private var retryCount = 0

    init {
        connectBillingClient()
    }

    private fun connectBillingClient() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Billing connected")
                    retryCount = 0
                    queryExistingPurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected, retrying")
                val delayMs = minOf(1000L * (1 shl retryCount), 30_000L)
                retryCount++
                scope.launch {
                    delay(delayMs)
                    connectBillingClient()
                }
            }
        })
    }

    /**
     * Authoritative purchase check. Sets isPro to match Google Play.
     * Always pushes result to watch so they stay in sync.
     */
    fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val proPurchase = purchases.firstOrNull { purchase ->
                    PRODUCT_ID in purchase.products &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                val hasPro = proPurchase != null
                if (proPurchase != null) acknowledgePurchase(proPurchase)
                setProState(hasPro)
                Log.i(TAG, "Purchase query complete: isPro=$hasPro")
            } else {
                Log.w(TAG, "Purchase query failed: ${result.debugMessage}")
                // Keep cached state on failure — push current state to watch anyway
                pushEntitlementToWatch()
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Purchase acknowledged")
            } else {
                Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    private fun setProState(isPro: Boolean) {
        val changed = _isPro.value != isPro
        _isPro.value = isPro
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
        if (changed) {
            Log.i(TAG, "Entitlement changed: isPro=$isPro")
        }
        // Always push on set — covers startup, purchase, and reconnect
        pushEntitlementToWatch()
    }

    fun launchPurchase(activity: Activity) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK ||
                productDetailsList.isEmpty()
            ) {
                Log.e(TAG, "Product details query failed: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }

            val productDetails = productDetailsList.first()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )
                )
                .build()

            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    /**
     * Called when watch requests entitlement status (PATH_ENTITLEMENT_REQUEST).
     * Responds directly to the requesting node.
     */
    fun handleEntitlementRequest(sourceNodeId: String) {
        scope.launch {
            try {
                val payload = if (_isPro.value) "1" else "0"
                Wearable.getMessageClient(appContext).sendMessage(
                    sourceNodeId, WearMessageConstants.PATH_ENTITLEMENT_RESPONSE,
                    payload.toByteArray()
                ).await()
                Log.d(TAG, "Responded to entitlement request: isPro=${_isPro.value}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to respond to entitlement request", e)
            }
        }
    }

    /**
     * Push entitlement to all connected watch nodes with retry on failure.
     */
    private fun pushEntitlementToWatch() {
        pushRetryJob?.cancel()
        pushRetryJob = scope.launch {
            var attempt = 0
            while (attempt < MAX_PUSH_RETRIES) {
                try {
                    val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                    if (nodes.isEmpty()) {
                        Log.d(TAG, "No watch nodes connected, skipping push")
                        return@launch
                    }
                    val payload = if (_isPro.value) "1" else "0"
                    for (node in nodes) {
                        Wearable.getMessageClient(appContext).sendMessage(
                            node.id, WearMessageConstants.PATH_ENTITLEMENT_PUSH,
                            payload.toByteArray()
                        ).await()
                    }
                    Log.d(TAG, "Pushed isPro=${_isPro.value} to ${nodes.size} watch nodes")
                    return@launch
                } catch (e: Exception) {
                    attempt++
                    if (attempt >= MAX_PUSH_RETRIES) {
                        Log.w(TAG, "Failed to push entitlement after $MAX_PUSH_RETRIES attempts", e)
                    } else {
                        val backoff = minOf(1000L * (1 shl attempt), 10_000L)
                        Log.d(TAG, "Push failed, retry $attempt in ${backoff}ms")
                        delay(backoff)
                    }
                }
            }
        }
    }
}
