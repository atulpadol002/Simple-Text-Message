package com.ap.simpletextmessage.ads

import android.content.Context
import android.net.Uri
import com.ap.simpletextmessage.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object AdRemoteConfigManager {
    private const val MASTER_KEY = "stm_ads_enabled"
    private const val LEGACY_MASTER_KEY = "ads_master_enabled"
    private const val TEST_MODE_KEY = "stm_ad_test_mode"
    private const val ALTERNATE_TEST_MODE_KEY = "stm_ads_test_mode"
    private const val LEGACY_TEST_MODE_KEY = "ads_test_mode"
    private const val CONFIG_KEY = "stm_ads_config"
    private const val LEGACY_CONFIG_KEY = "ads_config"
    private const val AUTO_INTERSTITIAL_CONFIG_KEY = "stm_auto_interstitial_config"
    private const val LEGACY_AUTO_INTERSTITIAL_CONFIG_KEY = "auto_interstitial_config"
    private const val AD_TYPE_CONFIG_KEY = "stm_ad_type_config"
    private const val LEGACY_AD_TYPE_CONFIG_KEY = "ad_type_config"
    private const val PAYWALL_ENABLED_KEY = "paywall_enabled"
    private const val COMMON_CONFIG_KEY = "common_config"
    const val PRIVACY_POLICY_FALLBACK_URL =
        "https://atulpadol002.github.io/Simple-Text-Message/privacy-policy.html"
    const val TERMS_CONDITIONS_FALLBACK_URL =
        "https://atulpadol002.github.io/Simple-Text-Message/terms-conditions.html"
    private const val RELEASE_FETCH_INTERVAL_SECONDS = 12 * 60 * 60L
    private const val DEBUG_FETCH_INTERVAL_SECONDS = 0L

    private val _config = MutableStateFlow(AdConfig.Defaults)
    val config: StateFlow<AdConfig> = _config.asStateFlow()
    private val _testMode = MutableStateFlow(true)
    val testMode: StateFlow<Boolean> = _testMode.asStateFlow()
    private val _autoInterstitialConfig = MutableStateFlow(AutoInterstitialConfig.Off)
    val autoInterstitialConfig: StateFlow<AutoInterstitialConfig> =
        _autoInterstitialConfig.asStateFlow()
    private val _adTypeConfig = MutableStateFlow(AdTypeConfig.CurrentBehaviorFallback)
    val adTypeConfig: StateFlow<AdTypeConfig> = _adTypeConfig.asStateFlow()
    private val _configurationRevision = MutableStateFlow(0L)
    val configurationRevision: StateFlow<Long> = _configurationRevision.asStateFlow()
    private val _paywallEnabled = MutableStateFlow(true)
    val paywallEnabled: StateFlow<Boolean> = _paywallEnabled.asStateFlow()
    private val _privacyPolicyUrl = MutableStateFlow(PRIVACY_POLICY_FALLBACK_URL)
    val privacyPolicyUrl: StateFlow<String> = _privacyPolicyUrl.asStateFlow()
    private val _termsConditionsUrl = MutableStateFlow(TERMS_CONDITIONS_FALLBACK_URL)
    val termsConditionsUrl: StateFlow<String> = _termsConditionsUrl.asStateFlow()
    private var fetchStarted = false
    private var appContext: Context? = null

    @Synchronized
    fun fetch(context: Context) {
        if (fetchStarted) {
            AdDebug.log { "Remote Config fetch skipped: already started in this process" }
            return
        }
        fetchStarted = true
        appContext = context.applicationContext

        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(
                if (BuildConfig.DEBUG) DEBUG_FETCH_INTERVAL_SECONDS else RELEASE_FETCH_INTERVAL_SECONDS
            )
            .build()
        AdDebug.log {
            "Remote Config fetch start: minimumIntervalSeconds=" +
                (if (BuildConfig.DEBUG) DEBUG_FETCH_INTERVAL_SECONDS else RELEASE_FETCH_INTERVAL_SECONDS)
        }

        remoteConfig.setConfigSettingsAsync(settings).addOnCompleteListener { settingsTask ->
            if (!settingsTask.isSuccessful) {
                AdDebug.log { "Remote Config settings failed: ${settingsTask.exception?.message}" }
            }
            remoteConfig.setDefaultsAsync(LOCAL_DEFAULTS).addOnCompleteListener { defaultsTask ->
                if (defaultsTask.isSuccessful) {
                    // Make the last activated values (or local defaults on first launch) available
                    // immediately. A network fetch is an update, not a startup dependency.
                    applyRemoteValues(remoteConfig, "cached_or_local_defaults")
                } else {
                    AdDebug.log { "Remote Config defaults failed: ${defaultsTask.exception?.message}" }
                    applyInMemoryDefaults()
                }

                remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                    AdDebug.log {
                        "Remote Config fetch/activate result: success=${task.isSuccessful}, " +
                            "changed=${if (task.isSuccessful) task.result else null}, " +
                            "lastFetchStatus=${remoteConfig.info.lastFetchStatus}, " +
                            "fetchTimeMillis=${remoteConfig.info.fetchTimeMillis}, " +
                            "error=${task.exception?.message}"
                    }
                    if (task.isSuccessful) {
                        applyRemoteValues(remoteConfig, "fetch_and_activate")
                    } else {
                        // Preserve the already-applied cached/default configuration.
                        logEffectiveConfig()
                    }
                }
            }
        }
    }

    private fun applyRemoteValues(remoteConfig: FirebaseRemoteConfig, source: String) {
        val masterValue = remoteConfig.selectValue(MASTER_KEY, LEGACY_MASTER_KEY)
        val testModeValue = remoteConfig.selectValue(
            TEST_MODE_KEY,
            ALTERNATE_TEST_MODE_KEY,
            LEGACY_TEST_MODE_KEY
        )
        val configValue = remoteConfig.selectValue(CONFIG_KEY, LEGACY_CONFIG_KEY)
        val autoInterstitialValue = remoteConfig.selectValue(
            AUTO_INTERSTITIAL_CONFIG_KEY,
            LEGACY_AUTO_INTERSTITIAL_CONFIG_KEY
        )
        val adTypeValue = remoteConfig.selectValue(AD_TYPE_CONFIG_KEY, LEGACY_AD_TYPE_CONFIG_KEY)
        val paywallEnabledValue = remoteConfig.getValue(PAYWALL_ENABLED_KEY)
        val commonConfigValue = remoteConfig.getValue(COMMON_CONFIG_KEY)
        val rawMaster = masterValue.value.asString()
        val rawJson = configValue.value.asString()
        val rawAutoInterstitialJson = autoInterstitialValue.value.asString()
        val rawAdTypeJson = adTypeValue.value.asString()
        AdDebug.log {
            "${masterValue.key} raw value=$rawMaster, boolean=${masterValue.value.asBoolean()}, " +
                "source=${masterValue.value.source}"
        }
        AdDebug.log {
            "${testModeValue.key} raw value=${testModeValue.value.asString()}, " +
                "boolean=${testModeValue.value.asBoolean()}, source=${testModeValue.value.source}"
        }
        AdDebug.log { "${configValue.key} raw JSON=$rawJson, source=${configValue.value.source}" }
        AdDebug.log {
            "${autoInterstitialValue.key} raw JSON=$rawAutoInterstitialJson, " +
                "source=${autoInterstitialValue.value.source}"
        }
        AdDebug.log {
            "${adTypeValue.key} raw JSON=$rawAdTypeJson, source=${adTypeValue.value.source}"
        }
        AdDebug.log {
            "$PAYWALL_ENABLED_KEY=${paywallEnabledValue.asBoolean()}, " +
                "source=${paywallEnabledValue.source}"
        }
        val masterEnabled = masterValue.value.asBoolean()
        val parsed = AdConfig.parse(masterEnabled, rawJson)
            ?: AdConfig.Defaults.copy(masterEnabled = masterEnabled)
        val parsedAutoInterstitial = AutoInterstitialConfig.parse(rawAutoInterstitialJson)
            ?: AutoInterstitialConfig.Defaults
        val parsedAdTypes = AdTypeConfig.parse(rawAdTypeJson)
        AdDebug.log { "parsed AdConfig=$parsed" }
        _config.value = parsed
        _testMode.value = testModeValue.value.asBoolean()
        _autoInterstitialConfig.value = parsedAutoInterstitial
        _adTypeConfig.value = parsedAdTypes
        _paywallEnabled.value = paywallEnabledValue.asBoolean()
        val commonConfig = parseCommonConfig(commonConfigValue.asString())
        _privacyPolicyUrl.value = commonConfig.privacyPolicyUrl
        _termsConditionsUrl.value = commonConfig.termsConditionsUrl
        invalidateLoadedAds(source)
        AutoInterstitialManager.onConfigUpdated(_autoInterstitialConfig.value)
        appContext?.let { context ->
            AdRuntime.preloadConfiguredAds(context, "remote_config_activated")
        }
        logEffectiveConfig()
    }

    private fun logEffectiveConfig() {
        AdDebug.log { "final effective master enabled=${_config.value.masterEnabled}" }
        AdDebug.log { "final effective test mode=${_testMode.value}" }
        AdDebug.log { "final paywall enabled=${_paywallEnabled.value}" }
        AdDebug.log { "final homeBanner.enabled=${_config.value.homeBanner.enabled}" }
        AdDebug.log {
            "final archiveNative.position=${_config.value.archiveNative.position.remoteValue}"
        }
        AdDebug.log {
            "final scheduleBanner.enabled=${_config.value.scheduleBanner.enabled} " +
                "adType=${_adTypeConfig.value[AdTypePlacement.SCHEDULED].remoteValue}"
        }
        AdDebug.log {
            "final serviceChatNative.enabled=${_config.value.serviceChatNative.enabled} " +
                "adType=${_adTypeConfig.value[AdTypePlacement.SERVICE_CHAT].remoteValue}"
        }
        AdDebug.log {
            "final onboardingGetStartedNative.enabled=" +
                "${_config.value.onboardingGetStartedNative.enabled} " +
                "adType=${_adTypeConfig.value[AdTypePlacement.ONBOARDING_GET_STARTED].remoteValue}"
        }
        AdDebug.log {
            "final defaultSmsNative.enabled=${_config.value.defaultSmsNative.enabled} " +
                "adType=${_adTypeConfig.value[AdTypePlacement.DEFAULT_SMS].remoteValue}"
        }
        AdDebug.log {
            "final auto interstitial enabled=" +
                _autoInterstitialConfig.value.enabled
        }
    }

    private fun applyInMemoryDefaults() {
        _config.value = AdConfig.Defaults
        _testMode.value = true
        _autoInterstitialConfig.value = AutoInterstitialConfig.Off
        _adTypeConfig.value = AdTypeConfig.CurrentBehaviorFallback
        _paywallEnabled.value = true
        _privacyPolicyUrl.value = PRIVACY_POLICY_FALLBACK_URL
        _termsConditionsUrl.value = TERMS_CONDITIONS_FALLBACK_URL
        invalidateLoadedAds("in_memory_defaults")
        AutoInterstitialManager.onConfigUpdated(AutoInterstitialConfig.Off)
        logEffectiveConfig()
    }

    private fun invalidateLoadedAds(source: String) {
        _configurationRevision.value = _configurationRevision.value + 1L
        InterstitialAdManager.onRemoteConfigChanged()
        AppOpenAdManager.onRemoteConfigChanged()
        RewardedAdManager.onRemoteConfigChanged()
        AdDebug.log {
            "Ad configuration revision=${_configurationRevision.value} source=$source; " +
                "previously loaded full-screen ads cleared"
        }
    }

    /**
     * Prefer an activated value from any supported key over local defaults. This keeps existing
     * Firebase projects working while allowing the stm-prefixed parameters to take precedence
     * once they are explicitly published.
     */
    private fun FirebaseRemoteConfig.selectValue(
        preferredKey: String,
        vararg compatibleKeys: String
    ): SelectedRemoteValue {
        val candidates = listOf(preferredKey, *compatibleKeys).map { key ->
            SelectedRemoteValue(key, getValue(key))
        }
        return candidates.firstOrNull {
            it.value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
        } ?: candidates.first()
    }

    private data class SelectedRemoteValue(
        val key: String,
        val value: FirebaseRemoteConfigValue
    )

    private val DEFAULT_ADS_CONFIG_JSON = """
        {
          "homeBanner":{"enabled":true},
          "homeInlineNative":{"enabled":true,"everyItems":4,"maxPerSession":50},
          "archiveNative":{"enabled":true,"maxPerSession":50,"position":"bottom"},
          "scheduleBanner":{"enabled":true},
          "blockedBanner":{"enabled":true},
          "starredBanner":{"enabled":true},
          "serviceChatNative":{"enabled":true,"maxPerSession":50},
          "newMessageNative":{"enabled":true,"maxPerSession":10},
          "languageNative":{"enabled":true,"maxPerSession":10},
          "onboardingGetStartedNative":{"enabled":true,"maxPerSession":10},
          "defaultSmsNative":{"enabled":true,"maxPerSession":10},
          "interstitial":{"enabled":true,"frequency":5,"minIntervalSeconds":10,"maxPerSession":20},
          "onboardingInterstitial":{"enabled":true,"maxPerSession":10},
          "appOpen":{"enabled":true,"showAfterOnboarding":true,"showOnResume":true,"minIntervalSeconds":10,"maxPerSession":50},
          "rewarded":{"restoreEnabled":true,"deleteForeverEnabled":true,"maxPerSession":50},
          "rateUsBanner":{"enabled":true},
          "exitDialogBanner":{"enabled":true},
          "chatBanner":{"enabled":true},
          "chatNative":{"enabled":true},
          "session":{"maxAds":70}
        }
    """.trimIndent()

    private val DEFAULT_COMMON_CONFIG_JSON = """
        {
          "privacyPolicyUrl":"$PRIVACY_POLICY_FALLBACK_URL",
          "termsConditionsUrl":"$TERMS_CONDITIONS_FALLBACK_URL"
        }
    """.trimIndent()

    private val DEFAULT_AD_TYPE_CONFIG_JSON = """
        {
          "home":"banner","homeInline":"native","archive":"native",
          "scheduled":"banner","blocked":"banner","starred":"banner",
          "serviceChat":"native","newMessage":"native","language":"native",
          "onboardingGetStarted":"native","defaultSms":"native",
          "chat":"banner","rateUs":"none","exit":"none",
          "normalInterstitial":"interstitial","autoInterstitial":"interstitial",
          "onboarding":"interstitial","appOpen":"app_open",
          "restore":"rewarded","deleteForever":"rewarded"
        }
    """.trimIndent()

    private val DEFAULT_AUTO_INTERSTITIAL_CONFIG_JSON = """
        {
          "enabled":true,
          "session1":{"enabled":true,"approach":"tap","home_tap":2,"is_tap":2,"back_tap":2,"repeat_tap":4},
          "session2":{"enabled":true,"approach":"tap","home_tap":2,"is_tap":2,"back_tap":2,"repeat_tap":4},
          "session3":{"enabled":true,"approach":"time","value":30,"home_tap":2,"is_tap":2,"back_tap":2,"repeat_tap":4},
          "session4":{"enabled":true,"approach":"time","value":45,"home_tap":2,"is_tap":2,"back_tap":2,"repeat_tap":4},
          "default":{"enabled":true,"approach":"tap","home_tap":2,"is_tap":2,"back_tap":2,"repeat_tap":4}
        }
    """.trimIndent()

    private val LOCAL_DEFAULTS: Map<String, Any>
        get() = mapOf(
            MASTER_KEY to false,
            LEGACY_MASTER_KEY to false,
            TEST_MODE_KEY to true,
            ALTERNATE_TEST_MODE_KEY to true,
            LEGACY_TEST_MODE_KEY to true,
            CONFIG_KEY to DEFAULT_ADS_CONFIG_JSON,
            LEGACY_CONFIG_KEY to DEFAULT_ADS_CONFIG_JSON,
            AUTO_INTERSTITIAL_CONFIG_KEY to DEFAULT_AUTO_INTERSTITIAL_CONFIG_JSON,
            LEGACY_AUTO_INTERSTITIAL_CONFIG_KEY to DEFAULT_AUTO_INTERSTITIAL_CONFIG_JSON,
            AD_TYPE_CONFIG_KEY to DEFAULT_AD_TYPE_CONFIG_JSON,
            LEGACY_AD_TYPE_CONFIG_KEY to DEFAULT_AD_TYPE_CONFIG_JSON,
            PAYWALL_ENABLED_KEY to true,
            COMMON_CONFIG_KEY to DEFAULT_COMMON_CONFIG_JSON
        )

    private data class CommonConfig(
        val privacyPolicyUrl: String,
        val termsConditionsUrl: String
    )

    private fun parseCommonConfig(rawJson: String): CommonConfig {
        return runCatching {
            val root = JSONObject(rawJson)
            CommonConfig(
                privacyPolicyUrl = validHttpUrlOrFallback(
                    root.optString("privacyPolicyUrl"),
                    PRIVACY_POLICY_FALLBACK_URL
                ),
                termsConditionsUrl = validHttpUrlOrFallback(
                    root.optString("termsConditionsUrl"),
                    TERMS_CONDITIONS_FALLBACK_URL
                )
            )
        }.getOrElse {
            AdDebug.log {
                "Common config parse rejected JSON: ${it.javaClass.simpleName}; using legal fallbacks"
            }
            CommonConfig(PRIVACY_POLICY_FALLBACK_URL, TERMS_CONDITIONS_FALLBACK_URL)
        }
    }

    private fun validHttpUrlOrFallback(value: String, fallback: String): String {
        val candidate = value.trim()
        val uri = runCatching { Uri.parse(candidate) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host
        return if (scheme in setOf("http", "https") && !host.isNullOrBlank()) {
            candidate
        } else {
            fallback
        }
    }
}
