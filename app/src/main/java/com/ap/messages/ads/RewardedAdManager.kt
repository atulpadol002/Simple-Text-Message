package com.ap.messages.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.ap.messages.premium.PremiumBillingManager

object RewardedAdManager {
    private val ads = mutableMapOf<AdPlacement, RewardedAd>()
    private val sources = mutableMapOf<AdPlacement, AdLoadSource>()
    private val loading = mutableSetOf<AdPlacement>()

    fun preload(context: Context, placement: AdPlacement) {
        val config = AdRemoteConfigManager.config.value
        val adTypes = AdRemoteConfigManager.adTypeConfig.value
        val enabled = isEnabled(placement, config, adTypes)
        val blockedReason = when {
            !enabled -> disabledReason(placement, config, adTypes)
            !AdRuntime.areAdsAllowed() -> "ads_not_allowed"
            !AdConsentManager.canRequestAds.value -> "can_request_ads_false"
            !AdRuntime.mobileAdsReady.value -> "mobile_ads_not_ready"
            placement in loading -> "load_in_progress"
            ads[placement] != null -> "already_ready"
            isCapped(config) -> "rewarded_session_cap"
            else -> null
        }
        if (blockedReason != null) {
            log(placement, "loadStarted=false blockedReason=$blockedReason")
            return
        }

        loading += placement
        load(context.applicationContext, placement, AdLoadSource.PRIMARY)
    }

    private fun load(context: Context, placement: AdPlacement, source: AdLoadSource) {
        val format = placement.loadFormat()
        log(placement, "loadStarted=true", source)
        AdDebug.log { "AdLoad format=$format source=$source started" }
        RewardedAd.load(
            context,
            AdUnitIds.rewarded(placement, source),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading -= placement
                    ads[placement] = ad
                    sources[placement] = source
                    AdDebug.log { "AdLoad format=$format source=$source loaded" }
                    log(placement, "loaded=true ready=true", source)
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    ads.remove(placement)
                    sources.remove(placement)
                    AdDebug.log {
                        "AdLoad format=$format source=$source failed code=${error.code}"
                    }
                    log(placement, "loaded=false loadErrorCode=${error.code}", source)
                    val primary = AdUnitIds.rewarded(placement, AdLoadSource.PRIMARY)
                    val backup = AdUnitIds.rewarded(placement, AdLoadSource.BACKUP)
                    if (
                        source == AdLoadSource.PRIMARY &&
                        AdUnitIds.hasDistinctBackup(primary, backup)
                    ) {
                        load(context, placement, AdLoadSource.BACKUP)
                    } else {
                        loading -= placement
                    }
                }
            }
        )
    }

    fun showOrFallback(
        activity: Activity,
        placement: AdPlacement,
        onReward: () -> Unit,
        onBypass: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val config = AdRemoteConfigManager.config.value
        val adTypes = AdRemoteConfigManager.adTypeConfig.value
        val typePlacement = placement.typePlacement()
        val typeAllowed = typePlacement != null &&
            adTypes.allows(typePlacement, AdType.REWARDED)
        if (!typeAllowed && typePlacement != null) adTypes.logBlocked(typePlacement)
        val enabled = isEnabled(placement, config, adTypes)
        val premium = PremiumBillingManager.state.value.isPremium
        val ready = ads[placement] != null
        val capped = isCapped(config)

        log(placement, "tap")
        log(placement, "enabled=$enabled")
        log(placement, "premium=$premium")
        log(placement, "ready=$ready")
        log(
            placement,
            "coordinatorFree=${FullScreenAdCoordinator.activeType() == null} " +
                "currentOwner=${FullScreenAdCoordinator.activeType()}"
        )

        val bypassReason = when {
            premium -> "premium_bypass"
            !enabled -> disabledReason(placement, config, adTypes)
            capped -> "rewarded_session_cap"
            else -> null
        }
        if (bypassReason != null) {
            log(placement, "blockedReason=$bypassReason")
            onBypass()
            return
        }

        val runtimeBlockedReason = when {
            !AdRuntime.areAdsAllowed() -> "ads_not_allowed"
            !AdConsentManager.canRequestAds.value -> "can_request_ads_false"
            !AdRuntime.mobileAdsReady.value -> "mobile_ads_not_ready"
            else -> null
        }
        if (runtimeBlockedReason != null) {
            log(placement, "blockedReason=$runtimeBlockedReason")
            onUnavailable()
            preload(activity, placement)
            return
        }

        val loaded = ads[placement]
        if (loaded == null || activity.isFinishing || activity.isDestroyed) {
            log(
                placement,
                "blockedReason=${if (loaded == null) "ad_not_ready" else "activity_unavailable"}"
            )
            onUnavailable()
            preload(activity, placement)
            return
        }
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.REWARDED)) {
            log(
                placement,
                "blockedReason=full_screen_active currentOwner=${FullScreenAdCoordinator.activeType()}"
            )
            onUnavailable()
            return
        }
        AdRuntime.suppressNextAppOpen()
        ads.remove(placement)
        var rewarded = false
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdSessionManager.recordRewardedShown(placement)
                log(placement, "shown=true")
            }
            override fun onAdDismissedFullScreenContent() {
                FullScreenAdCoordinator.release(FullScreenAdType.REWARDED)
                log(placement, "dismissed=true")
                if (rewarded) onReward()
                sources.remove(placement)
                preload(activity, placement)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                FullScreenAdCoordinator.release(FullScreenAdType.REWARDED)
                log(placement, "showErrorCode=${error.code}")
                onUnavailable()
                sources.remove(placement)
                preload(activity, placement)
            }
        }
        log(placement, "showRequested=true")
        runCatching {
            loaded.show(activity) {
                rewarded = true
                log(placement, "earned=true")
            }
        }.onFailure { error ->
            FullScreenAdCoordinator.release(FullScreenAdType.REWARDED)
            log(placement, "showErrorCode=exception:${error.javaClass.simpleName}")
            onUnavailable()
            sources.remove(placement)
            preload(activity, placement)
        }
    }

    fun logActivityUnavailable(placement: AdPlacement) {
        log(placement, "tap")
        log(placement, "blockedReason=activity_unavailable")
    }

    private fun isEnabled(
        placement: AdPlacement,
        config: AdConfig,
        adTypes: AdTypeConfig
    ): Boolean {
        val placementEnabled = when (placement) {
            AdPlacement.REWARDED_RESTORE -> config.rewarded.restoreEnabled
            AdPlacement.REWARDED_DELETE -> config.rewarded.deleteForeverEnabled
            else -> false
        }
        val typePlacement = placement.typePlacement() ?: return false
        return config.masterEnabled && placementEnabled &&
            adTypes.allows(typePlacement, AdType.REWARDED)
    }

    private fun disabledReason(
        placement: AdPlacement,
        config: AdConfig,
        adTypes: AdTypeConfig
    ): String = when {
        !config.masterEnabled -> "ads_master_disabled"
        placement.typePlacement() == null -> "unsupported_placement"
        !adTypes.allows(placement.typePlacement()!!, AdType.REWARDED) -> "ad_type_not_rewarded"
        placement == AdPlacement.REWARDED_RESTORE && !config.rewarded.restoreEnabled ->
            "placement_disabled"
        placement == AdPlacement.REWARDED_DELETE && !config.rewarded.deleteForeverEnabled ->
            "placement_disabled"
        else -> "placement_disabled"
    }

    private fun isCapped(config: AdConfig): Boolean =
        AdSessionManager.snapshot.value.rewardedShown >= config.rewarded.maxPerSession

    private fun AdPlacement.typePlacement(): AdTypePlacement? = when (this) {
        AdPlacement.REWARDED_RESTORE -> AdTypePlacement.RESTORE
        AdPlacement.REWARDED_DELETE -> AdTypePlacement.DELETE_FOREVER
        else -> null
    }

    private fun AdPlacement.loadFormat(): String = when (this) {
        AdPlacement.REWARDED_RESTORE -> "REWARDED_RESTORE"
        AdPlacement.REWARDED_DELETE -> "REWARDED_DELETE"
        else -> error("Unsupported Rewarded placement: $this")
    }

    private fun log(
        placement: AdPlacement,
        event: String,
        source: AdLoadSource? = sources[placement]
    ) {
        val name = when (placement) {
            AdPlacement.REWARDED_RESTORE -> "RESTORE"
            AdPlacement.REWARDED_DELETE -> "DELETE_FOREVER"
            else -> placement.name
        }
        AdDebug.log { "Rewarded placement=$name source=${source ?: "NONE"} $event" }
    }
}
