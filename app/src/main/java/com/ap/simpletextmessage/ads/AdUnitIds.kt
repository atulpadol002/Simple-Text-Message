package com.ap.simpletextmessage.ads

enum class AdLoadSource {
    PRIMARY,
    BACKUP
}

object AdUnitIds {
    val banner: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_BANNER else PRODUCTION_BANNER
    val bannerBackup: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_BANNER else PRODUCTION_BANNER_BACKUP
    val interstitial: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_INTERSTITIAL else PRODUCTION_INTERSTITIAL
    val interstitialBackup: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_INTERSTITIAL else PRODUCTION_INTERSTITIAL_BACKUP
    val restoreRewarded: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_REWARDED else PRODUCTION_REWARDED
    val restoreRewardedBackup: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_REWARDED else PRODUCTION_REWARDED_BACKUP
    val deleteForeverRewarded: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_REWARDED else PRODUCTION_REWARDED
    val deleteForeverRewardedBackup: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_REWARDED else PRODUCTION_REWARDED_BACKUP
    val native: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_NATIVE else PRODUCTION_NATIVE
    val nativeBackup: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_NATIVE else PRODUCTION_NATIVE_BACKUP
    val appOpen: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_APP_OPEN else PRODUCTION_APP_OPEN
    val appOpenBackup: String
        get() = if (AdRemoteConfigManager.testMode.value) TEST_APP_OPEN else PRODUCTION_APP_OPEN_BACKUP

    fun banner(source: AdLoadSource): String =
        if (source == AdLoadSource.PRIMARY) banner else bannerBackup

    fun interstitial(source: AdLoadSource): String =
        if (source == AdLoadSource.PRIMARY) interstitial else interstitialBackup

    fun rewarded(placement: AdPlacement, source: AdLoadSource): String = when (placement) {
        AdPlacement.REWARDED_RESTORE ->
            if (source == AdLoadSource.PRIMARY) restoreRewarded else restoreRewardedBackup
        AdPlacement.REWARDED_DELETE ->
            if (source == AdLoadSource.PRIMARY) deleteForeverRewarded else deleteForeverRewardedBackup
        else -> error("Unsupported Rewarded placement: $placement")
    }

    fun native(source: AdLoadSource): String =
        if (source == AdLoadSource.PRIMARY) native else nativeBackup

    fun appOpen(source: AdLoadSource): String =
        if (source == AdLoadSource.PRIMARY) appOpen else appOpenBackup

    fun hasDistinctBackup(primary: String, backup: String): Boolean = primary != backup

    private const val PRODUCTION_BANNER = "ca-app-pub-6067762135425385/5297743451"
    private const val PRODUCTION_BANNER_BACKUP = "ca-app-pub-6067762135425385/4124262587"
    private const val PRODUCTION_INTERSTITIAL = "ca-app-pub-6067762135425385/3409946715"
    private const val PRODUCTION_INTERSTITIAL_BACKUP = "ca-app-pub-6067762135425385/3232339977"
    private const val PRODUCTION_NATIVE = "ca-app-pub-6067762135425385/8676238341"
    private const val PRODUCTION_NATIVE_BACKUP = "ca-app-pub-6067762135425385/6558854234"
    private const val PRODUCTION_APP_OPEN = "ca-app-pub-6067762135425385/3026803336"
    private const val PRODUCTION_APP_OPEN_BACKUP = "ca-app-pub-6067762135425385/6116001109"
    private const val PRODUCTION_REWARDED = "ca-app-pub-6067762135425385/2821276435"
    private const val PRODUCTION_REWARDED_BACKUP = "ca-app-pub-6067762135425385/3106694381"

    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}
