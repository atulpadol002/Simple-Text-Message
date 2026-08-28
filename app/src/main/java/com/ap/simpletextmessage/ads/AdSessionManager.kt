package com.ap.simpletextmessage.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AdPlacement {
    HOME_BANNER,
    HOME_SURFACE_NATIVE,
    HOME_NATIVE,
    ARCHIVE_NATIVE,
    ARCHIVE_BANNER,
    BLOCKED_BANNER,
    BLOCKED_NATIVE,
    STARRED_BANNER,
    STARRED_NATIVE,
    SCHEDULED_BANNER,
    SERVICE_CHAT_NATIVE,
    NEW_MESSAGE_NATIVE,
    LANGUAGE_NATIVE,
    ONBOARDING_GET_STARTED_NATIVE,
    DEFAULT_SMS_NATIVE,
    CHAT_BANNER,
    AUTO_INTERSTITIAL,
    INTERSTITIAL,
    ONBOARDING_INTERSTITIAL,
    APP_OPEN,
    REWARDED_RESTORE,
    REWARDED_DELETE
}

data class AdSessionSnapshot(
    val totalAdsShown: Int = 0,
    val nonRewardedShown: Int = 0,
    val counts: Map<AdPlacement, Int> = emptyMap(),
    val interstitialEligibleActions: Int = 0,
    val lastInterstitialShownAt: Long = 0L,
    val lastAppOpenShownAt: Long = 0L
) {
    fun count(placement: AdPlacement): Int = counts[placement] ?: 0
    val rewardedShown: Int
        get() = count(AdPlacement.REWARDED_RESTORE) + count(AdPlacement.REWARDED_DELETE)
}

object AdSessionManager {
    private val _snapshot = MutableStateFlow(AdSessionSnapshot())
    val snapshot: StateFlow<AdSessionSnapshot> = _snapshot.asStateFlow()

    @Synchronized
    fun canShowAd(config: AdConfig): Boolean =
        AdRuntime.areAdsAllowed() && config.masterEnabled && config.sessionMaxAds > 0 &&
            _snapshot.value.totalAdsShown < config.sessionMaxAds

    @Synchronized
    fun recordNonRewardedShown(placement: AdPlacement) {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            totalAdsShown = current.totalAdsShown + 1,
            nonRewardedShown = current.nonRewardedShown + 1,
            counts = current.counts + (placement to current.count(placement) + 1),
            lastInterstitialShownAt = if (
                placement == AdPlacement.INTERSTITIAL ||
                placement == AdPlacement.AUTO_INTERSTITIAL
            ) {
                System.currentTimeMillis()
            } else current.lastInterstitialShownAt,
            lastAppOpenShownAt = if (placement == AdPlacement.APP_OPEN) {
                System.currentTimeMillis()
            } else current.lastAppOpenShownAt
        )
    }

    @Synchronized
    fun recordRewardedShown(placement: AdPlacement) {
        require(placement == AdPlacement.REWARDED_RESTORE || placement == AdPlacement.REWARDED_DELETE)
        val current = _snapshot.value
        _snapshot.value = current.copy(
            totalAdsShown = current.totalAdsShown + 1,
            counts = current.counts + (placement to current.count(placement) + 1)
        )
    }

    @Synchronized
    fun recordInterstitialEligibleAction(): Int {
        val updated = _snapshot.value.interstitialEligibleActions + 1
        _snapshot.value = _snapshot.value.copy(interstitialEligibleActions = updated)
        return updated
    }
}
