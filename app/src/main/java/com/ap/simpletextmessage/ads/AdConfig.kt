package com.ap.simpletextmessage.ads

import org.json.JSONObject

data class ToggleConfig(val enabled: Boolean = false)
data class NativeFeedConfig(
    val enabled: Boolean = false,
    val everyItems: Int = 7,
    val maxPerSession: Int = 0
)
data class CappedConfig(val enabled: Boolean = false, val maxPerSession: Int = 0)
enum class AdPosition(val remoteValue: String) {
    TOP("top"),
    BOTTOM("bottom")
}
data class PositionedCappedConfig(
    val enabled: Boolean = false,
    val maxPerSession: Int = 0,
    val position: AdPosition = AdPosition.BOTTOM
)
data class InterstitialConfig(
    val enabled: Boolean = false,
    val frequency: Int = Int.MAX_VALUE,
    val minIntervalSeconds: Long = Long.MAX_VALUE,
    val maxPerSession: Int = 0
)
data class AppOpenConfig(
    val enabled: Boolean = false,
    val showAfterOnboarding: Boolean = false,
    val showOnResume: Boolean = true,
    val minIntervalSeconds: Long = Long.MAX_VALUE,
    val maxPerSession: Int = 0
)
data class RewardedConfig(
    val restoreEnabled: Boolean = false,
    val deleteForeverEnabled: Boolean = false,
    val maxPerSession: Int = 0
)

data class AdConfig(
    val masterEnabled: Boolean = false,
    val homeBanner: ToggleConfig = ToggleConfig(),
    val homeInlineNative: NativeFeedConfig = NativeFeedConfig(),
    val archiveNative: PositionedCappedConfig = PositionedCappedConfig(),
    val scheduleBanner: ToggleConfig = ToggleConfig(),
    val serviceChatNative: CappedConfig = CappedConfig(),
    val newMessageNative: CappedConfig = CappedConfig(),
    val languageNative: CappedConfig = CappedConfig(),
    val onboardingGetStartedNative: CappedConfig = CappedConfig(),
    val defaultSmsNative: CappedConfig = CappedConfig(),
    val blockedBanner: ToggleConfig = ToggleConfig(),
    val starredBanner: ToggleConfig = ToggleConfig(),
    val interstitial: InterstitialConfig = InterstitialConfig(),
    val onboardingInterstitial: CappedConfig = CappedConfig(),
    val appOpen: AppOpenConfig = AppOpenConfig(),
    val rewarded: RewardedConfig = RewardedConfig(),
    val rateUsBanner: ToggleConfig = ToggleConfig(),
    val exitDialogBanner: ToggleConfig = ToggleConfig(),
    val chatBanner: ToggleConfig = ToggleConfig(),
    val chatNative: ToggleConfig = ToggleConfig(),
    val sessionMaxAds: Int = 0
) {
    companion object {
        val AllOff = AdConfig()

        val Defaults = AdConfig(
            masterEnabled = false,
            homeBanner = ToggleConfig(enabled = true),
            homeInlineNative = NativeFeedConfig(
                enabled = true,
                everyItems = 4,
                maxPerSession = 50
            ),
            archiveNative = PositionedCappedConfig(
                enabled = true,
                maxPerSession = 50,
                position = AdPosition.BOTTOM
            ),
            scheduleBanner = ToggleConfig(enabled = true),
            serviceChatNative = CappedConfig(enabled = true, maxPerSession = 50),
            newMessageNative = CappedConfig(enabled = true, maxPerSession = 10),
            languageNative = CappedConfig(enabled = true, maxPerSession = 10),
            onboardingGetStartedNative = CappedConfig(enabled = true, maxPerSession = 10),
            defaultSmsNative = CappedConfig(enabled = true, maxPerSession = 10),
            blockedBanner = ToggleConfig(enabled = true),
            starredBanner = ToggleConfig(enabled = true),
            interstitial = InterstitialConfig(
                enabled = true,
                frequency = 5,
                minIntervalSeconds = 10,
                maxPerSession = 20
            ),
            onboardingInterstitial = CappedConfig(enabled = true, maxPerSession = 10),
            appOpen = AppOpenConfig(
                enabled = true,
                showAfterOnboarding = true,
                showOnResume = true,
                minIntervalSeconds = 10,
                maxPerSession = 50
            ),
            rewarded = RewardedConfig(
                restoreEnabled = true,
                deleteForeverEnabled = true,
                maxPerSession = 50
            ),
            rateUsBanner = ToggleConfig(enabled = true),
            exitDialogBanner = ToggleConfig(enabled = true),
            chatBanner = ToggleConfig(enabled = true),
            chatNative = ToggleConfig(enabled = true),
            sessionMaxAds = 70
        )

        fun parse(masterEnabled: Boolean, json: String): AdConfig? = runCatching {
            val root = JSONObject(json)
            val homeBanner = root.requiredToggle("homeBanner")
            val homeNative = root.getJSONObject("homeInlineNative")
            val archiveNative = root.getJSONObject("archiveNative")
            val interstitial = root.getJSONObject("interstitial")
            val onboarding = root.getJSONObject("onboardingInterstitial")
            val appOpen = root.getJSONObject("appOpen")
            val rewarded = root.getJSONObject("rewarded")
            val session = root.getJSONObject("session")

            AdConfig(
                masterEnabled = masterEnabled,
                homeBanner = homeBanner,
                homeInlineNative = NativeFeedConfig(
                    enabled = homeNative.getBoolean("enabled"),
                    everyItems = homeNative.positiveInt("everyItems"),
                    maxPerSession = homeNative.nonNegativeInt("maxPerSession")
                ),
                archiveNative = PositionedCappedConfig(
                    archiveNative.getBoolean("enabled"),
                    archiveNative.nonNegativeInt("maxPerSession"),
                    archiveNative.optionalPosition("position")
                ),
                scheduleBanner = root.optionalToggle("scheduleBanner"),
                serviceChatNative = root.optionalCapped("serviceChatNative"),
                newMessageNative = root.optionalCapped("newMessageNative"),
                languageNative = root.optionalCapped("languageNative"),
                onboardingGetStartedNative = root.optionalCapped(
                    "onboardingGetStartedNative",
                    Defaults.onboardingGetStartedNative
                ),
                defaultSmsNative = root.optionalCapped(
                    "defaultSmsNative",
                    Defaults.defaultSmsNative
                ),
                blockedBanner = root.requiredToggle("blockedBanner"),
                starredBanner = root.requiredToggle("starredBanner"),
                interstitial = InterstitialConfig(
                    enabled = interstitial.getBoolean("enabled"),
                    frequency = interstitial.positiveInt("frequency"),
                    minIntervalSeconds = interstitial.nonNegativeLong("minIntervalSeconds"),
                    maxPerSession = interstitial.nonNegativeInt("maxPerSession")
                ),
                onboardingInterstitial = CappedConfig(
                    onboarding.getBoolean("enabled"),
                    onboarding.nonNegativeInt("maxPerSession")
                ),
                appOpen = AppOpenConfig(
                    enabled = appOpen.getBoolean("enabled"),
                    showAfterOnboarding = appOpen.optBoolean("showAfterOnboarding", false),
                    // Older configs showed App Open on resume whenever the placement was enabled.
                    showOnResume = appOpen.optBoolean("showOnResume", true),
                    minIntervalSeconds = appOpen.nonNegativeLong("minIntervalSeconds"),
                    maxPerSession = appOpen.nonNegativeInt("maxPerSession")
                ),
                rewarded = RewardedConfig(
                    restoreEnabled = rewarded.getBoolean("restoreEnabled"),
                    deleteForeverEnabled = rewarded.getBoolean("deleteForeverEnabled"),
                    maxPerSession = rewarded.nonNegativeInt("maxPerSession")
                ),
                rateUsBanner = root.requiredToggle("rateUsBanner"),
                exitDialogBanner = root.requiredToggle("exitDialogBanner"),
                chatBanner = root.requiredToggle("chatBanner"),
                chatNative = root.requiredToggle("chatNative"),
                sessionMaxAds = session.nonNegativeInt("maxAds")
            )
        }.onFailure { error ->
            AdDebug.log {
                "AdConfig parse rejected JSON: ${error.javaClass.simpleName}: ${error.message}"
            }
        }.getOrNull()
    }
}

private fun JSONObject.requiredToggle(name: String) =
    ToggleConfig(getJSONObject(name).getBoolean("enabled"))

private fun JSONObject.optionalToggle(name: String): ToggleConfig =
    optJSONObject(name)?.let { ToggleConfig(it.optBoolean("enabled", false)) } ?: ToggleConfig()

private fun JSONObject.optionalCapped(
    name: String,
    fallback: CappedConfig = CappedConfig()
): CappedConfig {
    val value = optJSONObject(name) ?: return fallback
    val maxPerSession = value.optInt("maxPerSession", fallback.maxPerSession)
    return CappedConfig(
        enabled = value.optBoolean("enabled", fallback.enabled),
        maxPerSession = maxPerSession.takeIf { it >= 0 } ?: fallback.maxPerSession
    )
}

private fun JSONObject.optionalPosition(name: String): AdPosition =
    when (optString(name, AdPosition.BOTTOM.remoteValue).lowercase()) {
        AdPosition.TOP.remoteValue -> AdPosition.TOP
        else -> AdPosition.BOTTOM
    }

private fun JSONObject.positiveInt(name: String): Int = getInt(name).also {
    require(it > 0) { "$name must be positive" }
}

private fun JSONObject.nonNegativeInt(name: String): Int = getInt(name).also {
    require(it >= 0) { "$name must not be negative" }
}

private fun JSONObject.nonNegativeLong(name: String): Long = getLong(name).also {
    require(it >= 0L) { "$name must not be negative" }
}
