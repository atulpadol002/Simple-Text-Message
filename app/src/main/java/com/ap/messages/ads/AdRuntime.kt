package com.ap.messages.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ap.messages.premium.PremiumBillingManager

object AdRuntime {
    private val _mobileAdsReady = MutableStateFlow(false)
    val mobileAdsReady: StateFlow<Boolean> = _mobileAdsReady.asStateFlow()
    private var initializationStarted = false
    private var suppressNextAppOpen = false
    val adsAllowed: StateFlow<Boolean> = PremiumBillingManager.adsAllowed

    fun initialize(activity: Activity) {
        AutoInterstitialManager.initialize(activity.applicationContext)
        AdRemoteConfigManager.fetch(activity.applicationContext)
    }

    fun gatherConsent(activity: Activity) {
        if (!areAdsAllowed()) {
            AdDebug.log { "Consent and ads skipped: premium entitlement is active or checking" }
            return
        }
        AdConsentManager.gatherConsent(activity) { allowed ->
            if (allowed) {
                initializeMobileAds(activity.applicationContext)
                preloadConfiguredAds(activity)
            } else {
                AdDebug.log { "MobileAds.initialize not called: canRequestAds=false" }
            }
        }
    }

    @Synchronized
    private fun initializeMobileAds(context: Context) {
        if (initializationStarted) {
            AdDebug.log { "MobileAds.initialize skipped: already started" }
            return
        }
        initializationStarted = true
        AdDebug.log { "MobileAds.initialize start; canRequestAds=${AdConsentManager.canRequestAds.value}" }
        MobileAds.initialize(context) { initializationStatus ->
            AdDebug.log { "MobileAds.initialize completion" }
            initializationStatus.adapterStatusMap.forEach { (adapter, status) ->
                AdDebug.log {
                    "MobileAds adapter=$adapter state=${status.initializationState} " +
                        "latencyMs=${status.latency} description=${status.description}"
                }
            }
            _mobileAdsReady.value = true
            preloadConfiguredAds(context)
        }
    }

    fun preloadConfiguredAds(context: Context) {
        if (!canLoadAds()) return
        InterstitialAdManager.preload(context)
        AppOpenAdManager.preload(context)
        RewardedAdManager.preload(context, AdPlacement.REWARDED_RESTORE)
        RewardedAdManager.preload(context, AdPlacement.REWARDED_DELETE)
    }

    fun canLoadAds(): Boolean =
        areAdsAllowed() && AdConsentManager.canRequestAds.value && _mobileAdsReady.value

    fun areAdsAllowed(): Boolean = PremiumBillingManager.adsAllowed.value

    @Synchronized
    fun suppressNextAppOpen() {
        suppressNextAppOpen = true
    }

    @Synchronized
    fun consumeAppOpenSuppression(): Boolean {
        val suppressed = suppressNextAppOpen
        suppressNextAppOpen = false
        return suppressed
    }
}
