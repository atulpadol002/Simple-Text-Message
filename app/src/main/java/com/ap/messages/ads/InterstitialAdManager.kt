package com.ap.messages.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialAdManager {
    private var ad: InterstitialAd? = null
    private var adSource: AdLoadSource? = null
    private var loading = false

    fun preload(context: Context) {
        val config = AdRemoteConfigManager.config.value
        val adTypes = AdRemoteConfigManager.adTypeConfig.value
        val normalEnabled = config.interstitial.enabled &&
            adTypes.allows(AdTypePlacement.NORMAL_INTERSTITIAL, AdType.INTERSTITIAL)
        val onboardingEnabled = config.onboardingInterstitial.enabled &&
            adTypes.allows(AdTypePlacement.ONBOARDING, AdType.INTERSTITIAL)
        val autoEnabled = AutoInterstitialManager.isEnabledForCurrentSession() &&
            adTypes.allows(AdTypePlacement.AUTO_INTERSTITIAL, AdType.INTERSTITIAL)
        if (loading || ad != null || !AdRuntime.canLoadAds() ||
            !config.masterEnabled ||
            (!normalEnabled && !onboardingEnabled && !autoEnabled) ||
            !AdSessionManager.canShowNonRewarded(config)
        ) return
        loading = true
        load(context.applicationContext, AdLoadSource.PRIMARY)
    }

    private fun load(context: Context, source: AdLoadSource) {
        if (!AdRuntime.canLoadAds("INTERSTITIAL", source)) {
            loading = false
            return
        }
        AdDebug.log { "AdLoad format=INTERSTITIAL source=$source started" }
        InterstitialAd.load(
            context,
            AdUnitIds.interstitial(source),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    loading = false
                    ad = loaded
                    adSource = source
                    AdDebug.log { "AdLoad format=INTERSTITIAL source=$source loaded" }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    adSource = null
                    AdDebug.log {
                        "AdLoad format=INTERSTITIAL source=$source failed code=${error.code}"
                    }
                    if (
                        source == AdLoadSource.PRIMARY &&
                        AdUnitIds.hasDistinctBackup(
                            AdUnitIds.interstitial,
                            AdUnitIds.interstitialBackup
                        )
                    ) {
                        load(context, AdLoadSource.BACKUP)
                    } else {
                        loading = false
                    }
                }
            }
        )
    }

    fun onEligibleTransition(
        activity: Activity,
        activitySafe: Boolean,
        event: AutoInterstitialEvent,
        allowNormalInterstitial: Boolean,
        proceed: () -> Unit
    ) {
        val config = AdRemoteConfigManager.config.value
        val adTypes = AdRemoteConfigManager.adTypeConfig.value
        val sessionAllowed = AdSessionManager.canShowNonRewarded(config)
        if (!activitySafe) {
            AutoInterstitialManager.onBlocked("activity is not presentation-safe")
            proceed()
            preload(activity)
            return
        }

        val autoTypeAllowed = adTypes.allows(
            AdTypePlacement.AUTO_INTERSTITIAL,
            AdType.INTERSTITIAL
        )
        val autoEligible = if (autoTypeAllowed) {
            AutoInterstitialManager.onEligibleEvent(
                event = event,
                masterEnabled = config.masterEnabled,
                sessionAllowed = sessionAllowed
            )
        } else {
            adTypes.logBlocked(AdTypePlacement.AUTO_INTERSTITIAL)
            AutoInterstitialManager.onBlocked("ad_type_none")
            false
        }
        if (autoEligible) {
            showAutoInterstitialOrProceed(activity, config, proceed)
            return
        }

        if (!allowNormalInterstitial) {
            proceed()
            preload(activity)
            return
        }

        val placement = config.interstitial
        val normalTypeAllowed = adTypes.allows(
            AdTypePlacement.NORMAL_INTERSTITIAL,
            AdType.INTERSTITIAL
        )
        if (!normalTypeAllowed) {
            adTypes.logBlocked(AdTypePlacement.NORMAL_INTERSTITIAL)
        }
        if (!config.masterEnabled || !placement.enabled || !normalTypeAllowed ||
            !AdRuntime.canLoadAds() || !sessionAllowed
        ) {
            proceed()
            preload(activity)
            return
        }
        val eligibleCount = AdSessionManager.recordInterstitialEligibleAction()
        val session = AdSessionManager.snapshot.value
        val intervalElapsed = System.currentTimeMillis() - session.lastInterstitialShownAt >=
            placement.minIntervalSeconds * 1000L
        val canShow = eligibleCount % placement.frequency == 0 && intervalElapsed &&
            session.count(AdPlacement.INTERSTITIAL) < placement.maxPerSession
        val loaded = ad
        if (!canShow || loaded == null || activity.isFinishing || activity.isDestroyed) {
            proceed()
            preload(activity)
            return
        }
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.NORMAL_INTERSTITIAL)) {
            proceed()
            preload(activity)
            return
        }
        AdRuntime.suppressNextAppOpen()
        ad = null
        adSource = null
        var proceeded = false
        fun continueOnce() {
            if (!proceeded) {
                proceeded = true
                proceed()
            }
        }
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdSessionManager.recordNonRewardedShown(AdPlacement.INTERSTITIAL)
            }
            override fun onAdDismissedFullScreenContent() {
                FullScreenAdCoordinator.release(FullScreenAdType.NORMAL_INTERSTITIAL)
                continueOnce()
                preload(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                FullScreenAdCoordinator.release(FullScreenAdType.NORMAL_INTERSTITIAL)
                continueOnce()
                preload(activity)
            }
        }
        runCatching { loaded.show(activity) }.onFailure {
            FullScreenAdCoordinator.release(FullScreenAdType.NORMAL_INTERSTITIAL)
            continueOnce()
            preload(activity)
        }
    }

    private fun showAutoInterstitialOrProceed(
        activity: Activity,
        config: AdConfig,
        proceed: () -> Unit
    ) {
        val loaded = ad
        val ready = loaded != null
        AutoInterstitialManager.onReadyState(ready)
        val blockedReason = when {
            !config.masterEnabled -> "ads master disabled"
            !AdSessionManager.canShowNonRewarded(config) -> "session/global cap reached"
            !AdRuntime.canLoadAds() -> "consent or Mobile Ads SDK not ready"
            activity.isFinishing || activity.isDestroyed -> "activity unavailable"
            loaded == null -> "interstitial ad not ready"
            FullScreenAdCoordinator.activeType() != null ->
                "full-screen presentation active=${FullScreenAdCoordinator.activeType()}"
            else -> null
        }
        if (blockedReason != null) {
            AutoInterstitialManager.onBlocked(blockedReason)
            proceed()
            preload(activity)
            return
        }
        checkNotNull(loaded)
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.AUTO_INTERSTITIAL)) {
            AutoInterstitialManager.onBlocked("another full-screen presentation acquired the gate")
            proceed()
            preload(activity)
            return
        }

        AdRuntime.suppressNextAppOpen()
        ad = null
        adSource = null
        var proceeded = false
        var impressionRecorded = false
        fun continueOnce() {
            if (!proceeded) {
                proceeded = true
                proceed()
            }
        }
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AutoInterstitialManager.onShown()
            }

            override fun onAdImpression() {
                if (!impressionRecorded) {
                    impressionRecorded = true
                    AdSessionManager.recordNonRewardedShown(AdPlacement.AUTO_INTERSTITIAL)
                    AutoInterstitialManager.onSuccessfulImpression()
                }
            }

            override fun onAdDismissedFullScreenContent() {
                FullScreenAdCoordinator.release(FullScreenAdType.AUTO_INTERSTITIAL)
                continueOnce()
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                FullScreenAdCoordinator.release(FullScreenAdType.AUTO_INTERSTITIAL)
                AutoInterstitialManager.onBlocked("failed to show: code=${error.code}")
                continueOnce()
                preload(activity)
            }
        }
        runCatching { loaded.show(activity) }.onFailure { error ->
            FullScreenAdCoordinator.release(FullScreenAdType.AUTO_INTERSTITIAL)
            AutoInterstitialManager.onBlocked(
                "show threw ${error.javaClass.simpleName}"
            )
            continueOnce()
            preload(activity)
        }
    }

    fun onOnboardingCompleted(activity: Activity, activitySafe: Boolean): Boolean {
        val config = AdRemoteConfigManager.config.value
        val adTypes = AdRemoteConfigManager.adTypeConfig.value
        val placement = config.onboardingInterstitial
        val session = AdSessionManager.snapshot.value
        val loaded = ad
        val typeAllowed = adTypes.allows(AdTypePlacement.ONBOARDING, AdType.INTERSTITIAL)
        if (!typeAllowed) adTypes.logBlocked(AdTypePlacement.ONBOARDING)
        if (!activitySafe || !config.masterEnabled || !placement.enabled || !typeAllowed ||
            !AdRuntime.canLoadAds() || !AdSessionManager.canShowNonRewarded(config) ||
            session.count(AdPlacement.ONBOARDING_INTERSTITIAL) >= placement.maxPerSession ||
            loaded == null || activity.isFinishing || activity.isDestroyed
        ) {
            preload(activity)
            return false
        }
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.ONBOARDING_INTERSTITIAL)) {
            preload(activity)
            return false
        }
        AdRuntime.suppressNextAppOpen()
        ad = null
        adSource = null
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdSessionManager.recordNonRewardedShown(AdPlacement.ONBOARDING_INTERSTITIAL)
            }
            override fun onAdDismissedFullScreenContent() {
                FullScreenAdCoordinator.release(FullScreenAdType.ONBOARDING_INTERSTITIAL)
                preload(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                FullScreenAdCoordinator.release(FullScreenAdType.ONBOARDING_INTERSTITIAL)
                preload(activity)
            }
        }
        runCatching { loaded.show(activity) }.onFailure {
            FullScreenAdCoordinator.release(FullScreenAdType.ONBOARDING_INTERSTITIAL)
            preload(activity)
        }
        return true
    }
}
