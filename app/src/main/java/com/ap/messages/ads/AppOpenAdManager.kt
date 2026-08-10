package com.ap.messages.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

enum class AppOpenReason(val logValue: String) {
    AFTER_ONBOARDING("after_onboarding"),
    WARM_RESUME("warm_resume")
}

object AppOpenAdManager {
    private const val MAX_AD_AGE_MILLIS = 4 * 60 * 60 * 1_000L
    private var ad: AppOpenAd? = null
    private var loading = false
    private var loadTime = 0L
    private var showing = false

    fun preload(context: Context) {
        val config = AdRemoteConfigManager.config.value
        val adTypes = AdRemoteConfigManager.adTypeConfig.value
        val typeAllowed = adTypes.allows(AdTypePlacement.APP_OPEN, AdType.APP_OPEN)
        if (loading || ad != null || !config.masterEnabled || !config.appOpen.enabled ||
            !typeAllowed || !AdRuntime.canLoadAds() ||
            !AdSessionManager.canShowNonRewarded(config)
        ) return
        loading = true
        AppOpenAd.load(
            context.applicationContext,
            AdUnitIds.appOpen,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    loading = false
                    ad = loaded
                    loadTime = System.currentTimeMillis()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    ad = null
                }
            }
        )
    }

    fun isReady(): Boolean = ad != null &&
        System.currentTimeMillis() - loadTime < MAX_AD_AGE_MILLIS

    fun maybeShow(
        activity: Activity,
        activitySafe: Boolean,
        reason: AppOpenReason
    ): Boolean {
        val config = AdRemoteConfigManager.config.value
        val adTypes = AdRemoteConfigManager.adTypeConfig.value
        val placement = config.appOpen
        val session = AdSessionManager.snapshot.value
        val reasonEnabled = when (reason) {
            AppOpenReason.AFTER_ONBOARDING -> placement.showAfterOnboarding
            AppOpenReason.WARM_RESUME -> placement.showOnResume
        }
        val typeAllowed = adTypes.allows(AdTypePlacement.APP_OPEN, AdType.APP_OPEN)
        val cooledDown = (System.currentTimeMillis() - session.lastAppOpenShownAt) / 1_000L >=
            placement.minIntervalSeconds
        val ready = isReady()
        val eligible = activitySafe && !showing && config.masterEnabled && placement.enabled &&
            reasonEnabled && typeAllowed && AdRuntime.canLoadAds() &&
            AdSessionManager.canShowNonRewarded(config) &&
            session.count(AdPlacement.APP_OPEN) < placement.maxPerSession && cooledDown &&
            !activity.isFinishing && !activity.isDestroyed &&
            FullScreenAdCoordinator.activeType() == null
        if (!typeAllowed) adTypes.logBlocked(AdTypePlacement.APP_OPEN)
        if (!eligible || !ready) {
            AdDebug.log {
                "App Open reason=${reason.logValue} eligible=$eligible ready=$ready shown=false"
            }
            if (!ready && ad != null) ad = null
            preload(activity)
            return false
        }

        val loaded = ad ?: return false
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.APP_OPEN)) {
            AdDebug.log {
                "App Open reason=${reason.logValue} eligible=false ready=true shown=false"
            }
            preload(activity)
            return false
        }
        AdRuntime.suppressNextAppOpen()
        showing = true
        ad = null
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdSessionManager.recordNonRewardedShown(AdPlacement.APP_OPEN)
                AdDebug.log {
                    "App Open reason=${reason.logValue} eligible=true ready=true shown=true"
                }
            }

            override fun onAdDismissedFullScreenContent() {
                showing = false
                FullScreenAdCoordinator.release(FullScreenAdType.APP_OPEN)
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                FullScreenAdCoordinator.release(FullScreenAdType.APP_OPEN)
                AdDebug.log {
                    "App Open reason=${reason.logValue} eligible=true ready=true shown=false"
                }
                preload(activity)
            }
        }
        return runCatching {
            loaded.show(activity)
            true
        }.getOrElse {
            showing = false
            FullScreenAdCoordinator.release(FullScreenAdType.APP_OPEN)
            AdDebug.log {
                "App Open reason=${reason.logValue} eligible=true ready=true shown=false"
            }
            preload(activity)
            false
        }
    }
}
