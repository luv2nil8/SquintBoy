package com.anaglych.squintboyadvance.presentation

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EntitlementRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "EntitlementRepo"
        private const val PREFS_NAME = "entitlement"
        private const val KEY_IS_PRO = "is_pro"
        private const val PRODUCT_ID = "squintboy_pro"

        @Volatile
        private var instance: EntitlementRepository? = null

        fun getInstance(context: Context): EntitlementRepository =
            instance ?: synchronized(this) {
                instance ?: EntitlementRepository(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_IS_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    val isDemo: Boolean get() = !_isPro.value

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    handlePurchase(purchase)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User cancelled purchase")
        } else {
            Log.w(TAG, "Purchase update failed: ${billingResult.debugMessage}")
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
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
                    Log.i(TAG, "Billing client connected")
                    retryCount = 0
                    queryExistingPurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected, will retry")
                val delay = minOf(1000L * (1 shl retryCount), 30_000L)
                retryCount++
                scope.launch {
                    delay(delay)
                    connectBillingClient()
                }
            }
        })
    }

    private fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPro = purchases.any { purchase ->
                    PRODUCT_ID in purchase.products &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (hasPro) {
                    setProState(true)
                    // Acknowledge any unacknowledged purchases
                    purchases.filter { !it.isAcknowledged && PRODUCT_ID in it.products }
                        .forEach { handlePurchase(it) }
                }
                Log.i(TAG, "Existing purchases queried: isPro=$hasPro")
            } else {
                Log.w(TAG, "Failed to query purchases: ${result.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
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
        setProState(true)
    }

    private fun setProState(isPro: Boolean) {
        _isPro.value = isPro
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
        pushEntitlementToPhone()
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
                Log.e(TAG, "Failed to query product details: ${result.debugMessage}")
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

    private fun pushEntitlementToPhone() {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                val payload = if (_isPro.value) "1" else "0"
                for (node in nodes) {
                    Wearable.getMessageClient(appContext).sendMessage(
                        node.id, WearMessageConstants.PATH_ENTITLEMENT_PUSH,
                        payload.toByteArray()
                    ).await()
                }
                Log.d(TAG, "Pushed entitlement to ${nodes.size} nodes: isPro=${_isPro.value}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push entitlement to phone", e)
            }
        }
    }

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
}
