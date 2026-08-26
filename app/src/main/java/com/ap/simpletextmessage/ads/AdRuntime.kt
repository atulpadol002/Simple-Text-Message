package com.ap.simpletextmessage.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.ap.simpletextmessage.premium.PremiumBillingManager

object AdRuntime {
    private val _mobileAdsReady = MutableStateFlow(false)
    val mobileAdsReady: StateFlow<Boolean> = _mobileAdsReady.asStateFlow()
    private var initializationStarted = false
    private var suppressNextAppOpen = false
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var readinessObserverStarted = false
    val adsAllowed: StateFlow<Boolean> = PremiumBillingManager.adsAllowed

    fun initialize(activity: Activity) {
        observeAppOpenReadiness(activity.applicationContext)
        AutoInterstitialManager.initialize(activity.applicationContext)
        AdRemoteConfigManager.fetch(activity.applicationContext)
    }

    fun gatherConsent(
        activity: Activity,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!areAdsAllowed()) {
            AdDebug.log { "Consent and ads skipped: premium entitlement is active or checking" }
            onComplete(false)
            return
        }
        AdConsentManager.gatherConsent(activity) { allowed ->
            if (allowed && AdRemoteConfigManager.config.value.masterEnabled) {
                initializeMobileAds(activity.applicationContext)
                preloadConfiguredAds(activity, "consent_callback")
            } else {
                AdDebug.log {
                    "MobileAds.initialize not called: canRequestAds=$allowed " +
                        "masterEnabled=${AdRemoteConfigManager.config.value.masterEnabled}"
                }
            }
            onComplete(allowed)
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
            preloadConfiguredAds(context, "sdk_ready")
        }
    }

    fun preloadConfiguredAds(context: Context, trigger: String = "configured_ads") {
        if (!canLoadAds()) return
        InterstitialAdManager.preload(context)
        AppOpenAdManager.preload(context, trigger)
        RewardedAdManager.preload(context, AdPlacement.REWARDED_RESTORE)
        RewardedAdManager.preload(context, AdPlacement.REWARDED_DELETE)
    }

    internal fun onConsentStateChanged(context: Context) {
        if (!AdConsentManager.canRequestAds.value) {
            AdDebug.log { "PrivacyOptions adRuntime=blocked_canRequestAds_false" }
            return
        }
        if (!areAdsAllowed()) {
            AdDebug.log { "PrivacyOptions adRuntime=blocked_premium_ads_suppressed" }
            return
        }
        if (!AdRemoteConfigManager.config.value.masterEnabled) {
            AdDebug.log { "PrivacyOptions adRuntime=blocked_master_disabled" }
            return
        }
        initializeMobileAds(context)
        preloadConfiguredAds(context, "privacy_options_updated")
    }

    @Synchronized
    private fun observeAppOpenReadiness(context: Context) {
        if (readinessObserverStarted) return
        readinessObserverStarted = true
        runtimeScope.launch {
            var previous: AppOpenReadiness? = null
            combine(
                AdRemoteConfigManager.config,
                AdRemoteConfigManager.adTypeConfig,
                adsAllowed,
                AdConsentManager.canRequestAds,
                mobileAdsReady
            ) { config, adTypes, allowed, consent, sdkReady ->
                AppOpenReadiness(
                    master = config.masterEnabled,
                    enabled = config.appOpen.enabled,
                    adTypeAllowed = adTypes.allows(
                        AdTypePlacement.APP_OPEN,
                        AdType.APP_OPEN
                    ),
                    adsAllowed = allowed,
                    consent = consent,
                    sdkReady = sdkReady
                )
            }.distinctUntilChanged().collect { current ->
                val trigger = current.triggerComparedWith(previous)
                previous = current
                if (current.master && current.adsAllowed && current.consent && !current.sdkReady) {
                    initializeMobileAds(context)
                }
                AppOpenAdManager.preload(context, trigger)
            }
        }
    }

    fun canLoadAds(): Boolean =
        AdRemoteConfigManager.config.value.masterEnabled &&
            areAdsAllowed() && AdConsentManager.canRequestAds.value && _mobileAdsReady.value

    fun canLoadAds(format: String, source: AdLoadSource): Boolean {
        val masterEnabled = AdRemoteConfigManager.config.value.masterEnabled
        val consent = AdConsentManager.canRequestAds.value
        val allowed = masterEnabled && areAdsAllowed() && consent && _mobileAdsReady.value
        when {
            !masterEnabled -> AdDebug.log {
                "Ads blocked because stm_ads_enabled=false format=$format source=$source"
            }
            !consent -> AdDebug.log {
                "Ads blocked because canRequestAds=false format=$format source=$source"
            }
            allowed -> AdDebug.log {
                "Ads allowed after canRequestAds=true format=$format source=$source"
            }
            else -> AdDebug.log {
                "Ads blocked despite canRequestAds=true format=$format source=$source " +
                    "masterEnabled=$masterEnabled adsAllowed=${areAdsAllowed()} " +
                    "sdkReady=${_mobileAdsReady.value}"
            }
        }
        return allowed
    }

    fun areAdsAllowed(): Boolean = PremiumBillingManager.adsAllowed.value

    private data class AppOpenReadiness(
        val master: Boolean,
        val enabled: Boolean,
        val adTypeAllowed: Boolean,
        val adsAllowed: Boolean,
        val consent: Boolean,
        val sdkReady: Boolean
    ) {
        fun triggerComparedWith(previous: AppOpenReadiness?): String = when {
            previous == null -> "runtime_observer_started"
            !previous.master && master -> "master_enabled"
            !previous.enabled && enabled -> "placement_enabled"
            !previous.adTypeAllowed && adTypeAllowed -> "ad_type_ready"
            !previous.adsAllowed && adsAllowed -> "premium_state_ready"
            !previous.consent && consent -> "consent_ready"
            !previous.sdkReady && sdkReady -> "sdk_ready"
            else -> "readiness_changed"
        }
    }

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
