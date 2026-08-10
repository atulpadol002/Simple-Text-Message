package com.ap.messages.ads

import com.ap.messages.BuildConfig

object AdUnitIds {
    val banner: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER else "ca-app-pub-6067762135425385/2002675901"
    val interstitial: String
        get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else "ca-app-pub-6067762135425385/6283721750"
    val restoreRewarded: String
        get() = if (BuildConfig.DEBUG) TEST_REWARDED else "ca-app-pub-6067762135425385/4395925011"
    val deleteForeverRewarded: String
        get() = if (BuildConfig.DEBUG) TEST_REWARDED else "ca-app-pub-6067762135425385/8143598330"
    val native: String
        get() = if (BuildConfig.DEBUG) TEST_NATIVE else "ca-app-pub-6067762135425385/1386618295"
    val appOpen: String
        get() = if (BuildConfig.DEBUG) TEST_APP_OPEN else "ca-app-pub-6067762135425385/9620331530"

    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}
