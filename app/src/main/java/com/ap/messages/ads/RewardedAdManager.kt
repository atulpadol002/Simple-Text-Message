package com.ap.messages.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object RewardedAdManager {
    private val ads = mutableMapOf<AdPlacement, RewardedAd>()
    private val loading = mutableSetOf<AdPlacement>()

    fun preload(context: Context, placement: AdPlacement) {
        val config = AdRemoteConfigManager.config.value
        val adTypes = AdRemoteConfigManager.adTypeConfig.value
        val enabled = when (placement) {
            AdPlacement.REWARDED_RESTORE -> config.rewarded.restoreEnabled &&
                adTypes.allows(AdTypePlacement.RESTORE, AdType.REWARDED)
            AdPlacement.REWARDED_DELETE -> config.rewarded.deleteForeverEnabled &&
                adTypes.allows(AdTypePlacement.DELETE_FOREVER, AdType.REWARDED)
            else -> false
        }
        if (!enabled || !config.masterEnabled || !AdRuntime.canLoadAds() ||
            placement in loading || ads[placement] != null ||
            AdSessionManager.snapshot.value.rewardedShown >= config.rewarded.maxPerSession
        ) return
        loading += placement
        val unitId = if (placement == AdPlacement.REWARDED_RESTORE) {
            AdUnitIds.restoreRewarded
        } else AdUnitIds.deleteForeverRewarded
        RewardedAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading -= placement
                    ads[placement] = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading -= placement
                    ads.remove(placement)
                }
            }
        )
    }

    fun showOrFallback(
        activity: Activity,
        placement: AdPlacement,
        onReward: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val config = AdRemoteConfigManager.config.value
        val adTypes = AdRemoteConfigManager.adTypeConfig.value
        val typePlacement = when (placement) {
            AdPlacement.REWARDED_RESTORE -> AdTypePlacement.RESTORE
            AdPlacement.REWARDED_DELETE -> AdTypePlacement.DELETE_FOREVER
            else -> null
        }
        val typeAllowed = typePlacement != null &&
            adTypes.allows(typePlacement, AdType.REWARDED)
        if (!typeAllowed && typePlacement != null) adTypes.logBlocked(typePlacement)
        val enabled = config.masterEnabled && typeAllowed && when (placement) {
            AdPlacement.REWARDED_RESTORE -> config.rewarded.restoreEnabled
            AdPlacement.REWARDED_DELETE -> config.rewarded.deleteForeverEnabled
            else -> false
        }
        val capped = AdSessionManager.snapshot.value.rewardedShown >=
            config.rewarded.maxPerSession
        if (!enabled || capped || !AdRuntime.canLoadAds()) {
            onUnavailable()
            return
        }
        val loaded = ads[placement]
        if (loaded == null || activity.isFinishing || activity.isDestroyed) {
            onUnavailable()
            preload(activity, placement)
            return
        }
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.REWARDED)) {
            onUnavailable()
            return
        }
        AdRuntime.suppressNextAppOpen()
        ads.remove(placement)
        var rewarded = false
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdSessionManager.recordRewardedShown(placement)
            }
            override fun onAdDismissedFullScreenContent() {
                FullScreenAdCoordinator.release(FullScreenAdType.REWARDED)
                if (rewarded) onReward()
                preload(activity, placement)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                FullScreenAdCoordinator.release(FullScreenAdType.REWARDED)
                onUnavailable()
                preload(activity, placement)
            }
        }
        runCatching {
            loaded.show(activity) { rewarded = true }
        }.onFailure {
            FullScreenAdCoordinator.release(FullScreenAdType.REWARDED)
            onUnavailable()
            preload(activity, placement)
        }
    }
}
