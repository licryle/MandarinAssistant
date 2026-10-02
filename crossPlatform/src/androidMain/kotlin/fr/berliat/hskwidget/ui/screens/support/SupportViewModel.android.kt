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
import co.touchlab.kermit.Logger
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
        try {
            val request = reviewManager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                try {
                    if (task.isSuccessful) {
                        // task.result has a platform type: the implicit cast to
                        // ReviewInfo can throw ClassCastException when the Play
                        // Services APK on device returns an unexpected type
                        // (seen as obfuscated zl0/o04 on Google devices).
                        val reviewInfo = try {
                            task.result
                        } catch (e: Exception) {
                            Logger.e(tag = TAG, messageString = "RequestReviewFlow_BadResultType", throwable = e)
                            Logging.logAnalyticsError(
                                TAG, "RequestReviewFlow_BadResultType",
                                "${e.javaClass.name}: ${e.message}"
                            )
                            HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.support_review_failed)
                            return@addOnCompleteListener
                        }
                        try {
                            if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
                            val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                            flow.addOnCompleteListener { launchTask ->
                                try {
                                    if (launchTask.isSuccessful) {
                                        HSKAppServices.snackbar.show(SnackbarType.INFO, Res.string.support_reviewed)
                                    } else {
                                        HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.support_review_failed)
                                        Logger.e(tag = TAG, messageString = "LaunchReviewFlow_Failed", throwable = launchTask.exception)
                                    }
                                } catch (e: Exception) {
                                    Logger.e(tag = TAG, messageString = "LaunchReviewFlow_CallbackFailed", throwable = e)
                                    try {
                                        HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.support_review_failed)
                                    } catch (_: Exception) { }
                                }
                            }
                        } catch (e: Exception) {
                            Logger.e(tag = TAG, messageString = "LaunchReviewFlow_SetupFailed", throwable = e)
                            Logging.logAnalyticsError(
                                TAG, "LaunchReviewFlow_SetupFailed",
                                "${e.javaClass.name}: ${e.message}"
                            )
                            HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.support_review_failed)
                        }
                    } else {
                        HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.support_review_failed)
                        // Safe cast: task.exception is often NOT a ReviewException
                        // (ApiException, RuntimeExecutionException, ClassCastException
                        // from GMS internals...). The previous unsafe cast crashed here.
                        val reviewException = task.exception as? ReviewException
                        @ReviewErrorCode val reviewErrorCode = reviewException?.errorCode
                        val details = if (reviewErrorCode != null) {
                            reviewErrorCode.toString()
                        } else {
                            val e = task.exception
                            "${e?.javaClass?.name}: ${e?.message}"
                        }
                        Logger.e(tag = TAG, messageString = "RequestReviewFlow_SetupFailed", throwable = task.exception)
                        Logging.logAnalyticsError(TAG, "RequestReviewFlow_SetupFailed", details)
                    }
                } catch (e: Exception) {
                    // Last-resort guard: never crash from the review callback.
                    Logger.e(tag = TAG, messageString = "TriggerReview_CallbackFailed", throwable = e)
                    Logging.logAnalyticsError(
                        TAG, "TriggerReview_CallbackFailed",
                        "${e.javaClass.name}: ${e.message}"
                    )
                    try {
                        HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.support_review_failed)
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "TriggerReview_SetupFailed", throwable = e)
            Logging.logAnalyticsError(
                TAG, "TriggerReview_SetupFailed",
                "${e.javaClass.name}: ${e.message}"
            )
            try {
                HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.support_review_failed)
            } catch (_: Exception) { }
        }
    }

    fun getFirstProductId(purchase: Purchase?): String {
        return purchase?.products?.first() ?: ""
    }

    companion object {
        private const val TAG = "SupportViewModel"
    }
}