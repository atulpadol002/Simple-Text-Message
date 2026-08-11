package com.ap.messages.ads

import com.ap.messages.BuildConfig

enum class AdLoadSource {
    PRIMARY,
    BACKUP
}

object AdUnitIds {
    val banner: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER else "ca-app-pub-6067762135425385/2002675901"
    val bannerBackup: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER else "ca-app-pub-6067762135425385/1358235113"
    val interstitial: String
        get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else "ca-app-pub-6067762135425385/6283721750"
    val interstitialBackup: String
        get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else "ca-app-pub-6067762135425385/2068681557"
    val restoreRewarded: String
        get() = if (BuildConfig.DEBUG) TEST_REWARDED else "ca-app-pub-6067762135425385/4395925011"
    val restoreRewardedBackup: String
        get() = if (BuildConfig.DEBUG) TEST_REWARDED else "ca-app-pub-6067762135425385/6227418410"
    val deleteForeverRewarded: String
        get() = if (BuildConfig.DEBUG) TEST_REWARDED else "ca-app-pub-6067762135425385/8143598330"
    val deleteForeverRewardedBackup: String
        get() = if (BuildConfig.DEBUG) TEST_REWARDED else "ca-app-pub-6067762135425385/8442518213"
    val native: String
        get() = if (BuildConfig.DEBUG) TEST_NATIVE else "ca-app-pub-6067762135425385/1386618295"
    val nativeBackup: String
        get() = if (BuildConfig.DEBUG) TEST_NATIVE else "ca-app-pub-6067762135425385/5254228036"
    val appOpen: String
        get() = if (BuildConfig.DEBUG) TEST_APP_OPEN else "ca-app-pub-6067762135425385/9620331530"
    val appOpenBackup: String
        get() = if (BuildConfig.DEBUG) TEST_APP_OPEN else "ca-app-pub-6067762135425385/6172292102"

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

    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}
