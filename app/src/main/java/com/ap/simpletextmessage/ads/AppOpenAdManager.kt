package com.ap.simpletextmessage.ads

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.ap.simpletextmessage.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.ap.simpletextmessage.premium.PremiumBillingManager

enum class AppOpenReason(val logValue: String) {
    AFTER_ONBOARDING("after_onboarding"),
    WARM_RESUME("warm_resume")
}

object AppOpenAdManager {
    private const val MAX_AD_AGE_MILLIS = 4 * 60 * 60 * 1_000L
    private var ad: AppOpenAd? = null
    private var adSource: AdLoadSource? = null
    private var loadingSource: AdLoadSource? = null
    private var loading = false
    private var loadTime = 0L
    private var showing = false

    fun preload(context: Context, trigger: String = "direct") {
        val config = AdRemoteConfigManager.config.value
        val adTypes = AdRemoteConfigManager.adTypeConfig.value
        val typeAllowed = adTypes.allows(AdTypePlacement.APP_OPEN, AdType.APP_OPEN)
        val premium = PremiumBillingManager.state.value.isPremium
        val consent = AdConsentManager.canRequestAds.value
        val sdkReady = AdRuntime.mobileAdsReady.value
        val ready = isReady()
        AdDebug.log {
            "AppOpen preload trigger=$trigger master=${config.masterEnabled} " +
                "enabled=${config.appOpen.enabled} " +
                "adType=${adTypes[AdTypePlacement.APP_OPEN].remoteValue} premium=$premium " +
                "consent=$consent sdkReady=$sdkReady loading=$loading ready=$ready " +
                "source=${adSource ?: loadingSource ?: "NONE"}"
        }
        if (loading || ready || !config.masterEnabled || !config.appOpen.enabled ||
            !typeAllowed || premium || !AdRuntime.areAdsAllowed() || !consent || !sdkReady ||
            !AdSessionManager.canShowAd(config)
        ) return
        loading = true
        load(
            context.applicationContext,
            AdLoadSource.PRIMARY,
            AdRemoteConfigManager.configurationRevision.value
        )
    }

    private fun load(context: Context, source: AdLoadSource, revision: Long) {
        if (revision != AdRemoteConfigManager.configurationRevision.value) return
        if (!AdRuntime.canLoadAds("APP_OPEN", source)) {
            loading = false
            loadingSource = null
            return
        }
        loadingSource = source
        AdDebug.log { "AdLoad format=APP_OPEN source=$source started" }
        AppOpenAd.load(
            context,
            AdUnitIds.appOpen(source),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    if (revision != AdRemoteConfigManager.configurationRevision.value) return
                    loading = false
                    loadingSource = null
                    ad = loaded
                    adSource = source
                    loadTime = System.currentTimeMillis()
                    AdDebug.log { "AdLoad format=APP_OPEN source=$source loaded" }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (revision != AdRemoteConfigManager.configurationRevision.value) return
                    ad = null
                    adSource = null
                    AdDebug.log {
                        "AdLoad format=APP_OPEN source=$source failed code=${error.code}"
                    }
                    if (
                        source == AdLoadSource.PRIMARY &&
                        AdUnitIds.hasDistinctBackup(AdUnitIds.appOpen, AdUnitIds.appOpenBackup)
                    ) {
                        load(context, AdLoadSource.BACKUP, revision)
                    } else {
                        loading = false
                        loadingSource = null
                    }
                }
            }
        )
    }

    fun isReady(): Boolean {
        val ready = ad != null && System.currentTimeMillis() - loadTime < MAX_AD_AGE_MILLIS
        if (!ready && ad != null) {
            ad = null
            adSource = null
        }
        return ready
    }

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
        val coordinatorFree = FullScreenAdCoordinator.activeType() == null
        val lifecycleState = (activity as? LifecycleOwner)?.lifecycle?.currentState
        val activityResumed = lifecycleState?.isAtLeast(Lifecycle.State.RESUMED) == true
        val onMainThread = Looper.myLooper() == Looper.getMainLooper()
        AdDebug.log {
            "AppOpen show activity=${activity.javaClass.simpleName} " +
                "isFinishing=${activity.isFinishing} isDestroyed=${activity.isDestroyed} " +
                "lifecycle=${lifecycleState ?: "UNAVAILABLE"} mainThread=$onMainThread"
        }
        val eligible = activitySafe && activityResumed && onMainThread && !showing &&
            config.masterEnabled && placement.enabled &&
            reasonEnabled && typeAllowed && AdRuntime.canLoadAds() &&
            AdSessionManager.canShowAd(config) &&
            session.count(AdPlacement.APP_OPEN) < placement.maxPerSession && cooledDown &&
            !activity.isFinishing && !activity.isDestroyed &&
            coordinatorFree
        val blockedReason = when {
            !activitySafe -> "activity_not_safe"
            !activityResumed -> "activity_not_resumed"
            !onMainThread -> "not_main_thread"
            showing -> "already_showing"
            !config.masterEnabled -> "ads_master_disabled"
            !placement.enabled -> "placement_disabled"
            !reasonEnabled -> "reason_disabled"
            !typeAllowed -> "ad_type_not_app_open"
            !AdRuntime.areAdsAllowed() -> "premium_or_ads_not_allowed"
            !AdConsentManager.canRequestAds.value -> "can_request_ads_false"
            !AdRuntime.mobileAdsReady.value -> "mobile_ads_not_ready"
            !AdSessionManager.canShowAd(config) -> "session_global_cap"
            session.count(AdPlacement.APP_OPEN) >= placement.maxPerSession -> "placement_cap"
            !cooledDown -> "minimum_interval"
            activity.isFinishing || activity.isDestroyed -> "activity_unavailable"
            !coordinatorFree -> "full_screen_active"
            !ready -> "not_ready"
            else -> "none"
        }
        if (!typeAllowed) adTypes.logBlocked(AdTypePlacement.APP_OPEN)
        if (!eligible || !ready) {
            AdDebug.log {
                "AppOpen show reason=${reason.logValue} eligible=$eligible ready=$ready " +
                    "coordinatorFree=$coordinatorFree shown=false blockedReason=$blockedReason"
            }
            preload(activity, "show_${reason.logValue}_blocked")
            return false
        }

        val loaded = ad ?: return false
        val loadedSource = adSource
        val appContext = activity.applicationContext
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.APP_OPEN)) {
            AdDebug.log {
                "AppOpen show reason=${reason.logValue} eligible=false ready=true " +
                    "coordinatorFree=false shown=false blockedReason=coordinator_race"
            }
            preload(activity, "show_${reason.logValue}_coordinator_race")
            return false
        }
        AdRuntime.suppressNextAppOpen()
        showing = true
        ad = null
        adSource = null
        loadTime = 0L
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdSessionManager.recordNonRewardedShown(AdPlacement.APP_OPEN)
                AdDebug.log {
                    "AppOpen show reason=${reason.logValue} eligible=true ready=true " +
                        "coordinatorFree=true shown=true blockedReason=none source=$loadedSource"
                }
            }

            override fun onAdDismissedFullScreenContent() {
                showing = false
                FullScreenAdCoordinator.release(FullScreenAdType.APP_OPEN)
                preload(appContext, "dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                ad = null
                adSource = null
                loadTime = 0L
                FullScreenAdCoordinator.release(FullScreenAdType.APP_OPEN)
                AdDebug.log {
                    "AppOpen show reason=${reason.logValue} eligible=true ready=true " +
                        "coordinatorFree=true shown=false blockedReason=failed_to_show"
                }
                preload(appContext, "failed_to_show")
            }
        }
        return runCatching {
            loaded.show(activity)
            true
        }.getOrElse { throwable ->
            showing = false
            ad = null
            adSource = null
            loadTime = 0L
            FullScreenAdCoordinator.release(FullScreenAdType.APP_OPEN)
            if (BuildConfig.DEBUG) {
                Log.e(
                    AdDebug.TAG,
                    "AppOpen show exception class=${throwable.javaClass.name} " +
                        "message=${throwable.message}",
                    throwable
                )
            }
            AdDebug.log {
                "AppOpen show reason=${reason.logValue} eligible=true ready=true " +
                    "coordinatorFree=true shown=false blockedReason=show_exception"
            }
            preload(appContext, "show_exception")
            false
        }
    }

    @Synchronized
    fun onRemoteConfigChanged() {
        ad = null
        adSource = null
        loadingSource = null
        loading = false
        loadTime = 0L
    }
}
