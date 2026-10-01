package fr.berliat.hskwidget.ui.screens.support

import android.app.Activity
import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase

import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode

import fr.berliat.hskwidget.core.ExpectedUtils
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.data.store.SupportDevStore

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.support_payment_failed
import fr.berliat.hskwidget.support_payment_success
import fr.berliat.hskwidget.support_review_failed
import fr.berliat.hskwidget.support_reviewed
import fr.berliat.hskwidget.support_total_error

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

actual class SupportViewModel(
    val supportDevStore : SupportDevStore = SupportDevStore.getInstance(ExpectedUtils.context),
    val contextProvider: () -> Context = { ExpectedUtils.context },
    val reviewManager: ReviewManager = ReviewManagerFactory.create(ExpectedUtils.context),
    val appConfig : AppPreferencesStore = HSKAppServices.appPreferences,
) : ViewModel(),
    SupportDevStore.SupportDevListener {
    val totalSpent: StateFlow<Float> = appConfig.supportTotalSpent.asStateFlow()

    private val _purchaseList =
        MutableStateFlow(supportDevStore.purchases.toMap())
    val purchaseList: StateFlow<Map<SupportDevStore.SupportProduct, Int>> = _purchaseList

    init {
        onTotalSpentChange(appConfig.supportTotalSpent.value)
        supportDevStore.addListener(this)
    }

    fun fetchPurchases() {
        supportDevStore.connect()
    }

    fun supportTier() = SupportDevStore.getSupportTier(totalSpent.value)

    fun makePurchase(activity: Activity, productId: String) {
        supportDevStore.makePurchase(activity, productId)
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.PURCHASE_CLICK,
            mapOf("product_id" to productId)
        )
    }

    override fun onTotalSpentChange(totalSpent: Float) {
        appConfig.supportTotalSpent.value = totalSpent
    }

    override fun onQueryFailure(result: BillingResult) {
        HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.support_total_error)
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.SUPPORT_FETCH_FAIL,
            mapOf("CODE" to result.responseCode.toString())
        )
    }

    override fun onPurchaseSuccess(purchase: Purchase) {
        HSKAppServices.snackbar.show(SnackbarType.SUCCESS, Res.string.support_payment_success)
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.PURCHASE_SUCCESS,
            mapOf("product_id" to getFirstProductId(purchase))
        )
    }

    override fun onPurchaseHistoryUpdate(purchases: Map<SupportDevStore.SupportProduct, Int>) {
        viewModelScope.launch {
            _purchaseList.emit(purchases)
        }
    }

    override fun onPurchaseAcknowledgedSuccess(purchase: Purchase) { }

    override fun onPurchaseFailure(purchase: Purchase?, billingResponseCode: Int) {
        HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.support_payment_failed)
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.PURCHASE_FAILED,
            mapOf("product_id" to getFirstProductId(purchase))
        )
    }

    fun triggerReview(activity: Activity) {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    HSKAppServices.snackbar.show(SnackbarType.INFO, Res.string.support_reviewed)
                }
            } else {
                HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.support_review_failed)
                @ReviewErrorCode val reviewErrorCode = (task.exception as ReviewException).errorCode
                Logging.logAnalyticsError(TAG, "RequestReviewFlow_SetupFailed", reviewErrorCode.toString())
            }
        }
    }

    fun getFirstProductId(purchase: Purchase?): String {
        return purchase?.products?.first() ?: ""
    }

    companion object {
        private const val TAG = "SupportViewModel"
    }
}