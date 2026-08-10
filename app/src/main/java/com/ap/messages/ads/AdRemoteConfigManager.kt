package com.ap.messages.ads

import android.content.Context
import com.ap.messages.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdRemoteConfigManager {
    private const val MASTER_KEY = "ads_master_enabled"
    private const val CONFIG_KEY = "ads_config"
    private const val AUTO_INTERSTITIAL_CONFIG_KEY = "auto_interstitial_config"
    private const val AD_TYPE_CONFIG_KEY = "ad_type_config"
    private const val RELEASE_FETCH_INTERVAL_SECONDS = 12 * 60 * 60L
    private const val DEBUG_FETCH_INTERVAL_SECONDS = 0L

    private val _config = MutableStateFlow(AdConfig.AllOff)
    val config: StateFlow<AdConfig> = _config.asStateFlow()
    private val _autoInterstitialConfig = MutableStateFlow(AutoInterstitialConfig.Off)
    val autoInterstitialConfig: StateFlow<AutoInterstitialConfig> =
        _autoInterstitialConfig.asStateFlow()
    private val _adTypeConfig = MutableStateFlow(AdTypeConfig.CurrentBehaviorFallback)
    val adTypeConfig: StateFlow<AdTypeConfig> = _adTypeConfig.asStateFlow()
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
        disableAllAds()

        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(
                if (BuildConfig.DEBUG) DEBUG_FETCH_INTERVAL_SECONDS else RELEASE_FETCH_INTERVAL_SECONDS
            )
            .build()
        remoteConfig.setDefaultsAsync(
            mapOf(
                MASTER_KEY to false,
                CONFIG_KEY to "{}",
                AUTO_INTERSTITIAL_CONFIG_KEY to "{}",
                AD_TYPE_CONFIG_KEY to "{}"
            )
        )

        AdDebug.log {
            "Remote Config fetch start: minimumIntervalSeconds=" +
                (if (BuildConfig.DEBUG) DEBUG_FETCH_INTERVAL_SECONDS else RELEASE_FETCH_INTERVAL_SECONDS)
        }

        remoteConfig.setConfigSettingsAsync(settings).addOnCompleteListener { settingsTask ->
            if (!settingsTask.isSuccessful) {
                AdDebug.log { "Remote Config settings failed: ${settingsTask.exception?.message}" }
                disableAllAds()
                return@addOnCompleteListener
            }
            if (BuildConfig.DEBUG) {
                remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                    AdDebug.log {
                        "Remote Config fetch result: success=${task.isSuccessful}, " +
                            "lastFetchStatus=${remoteConfig.info.lastFetchStatus}, " +
                            "fetchTimeMillis=${remoteConfig.info.fetchTimeMillis}, " +
                            "error=${task.exception?.message}"
                    }
                    AdDebug.log {
                        "Remote Config activate result: success=${task.isSuccessful}, " +
                            "changed=${if (task.isSuccessful) task.result else null}"
                    }
                    if (task.isSuccessful) applyRemoteValues(remoteConfig) else {
                        disableAllAds()
                        logEffectiveConfig()
                    }
                }
                return@addOnCompleteListener
            }

            val requestStartedAt = System.currentTimeMillis()
            remoteConfig.fetch().addOnSuccessListener {
                // A throttled/min-interval cache hit is not enough to enable ads on this launch.
                if (remoteConfig.info.fetchTimeMillis < requestStartedAt) {
                    disableAllAds()
                    return@addOnSuccessListener
                }
                remoteConfig.activate().addOnCompleteListener { activation ->
                    if (!activation.isSuccessful) {
                        disableAllAds()
                        return@addOnCompleteListener
                    }
                    applyRemoteValues(remoteConfig)
                }
            }.addOnFailureListener {
                disableAllAds()
            }
        }
    }

    private fun applyRemoteValues(remoteConfig: FirebaseRemoteConfig) {
        val masterValue = remoteConfig.getValue(MASTER_KEY)
        val configValue = remoteConfig.getValue(CONFIG_KEY)
        val autoInterstitialValue = remoteConfig.getValue(AUTO_INTERSTITIAL_CONFIG_KEY)
        val adTypeValue = remoteConfig.getValue(AD_TYPE_CONFIG_KEY)
        val rawMaster = masterValue.asString()
        val rawJson = configValue.asString()
        val rawAutoInterstitialJson = autoInterstitialValue.asString()
        val rawAdTypeJson = adTypeValue.asString()
        AdDebug.log {
            "ads_master_enabled raw value=$rawMaster, boolean=${masterValue.asBoolean()}, " +
                "source=${masterValue.source}"
        }
        AdDebug.log { "ads_config raw JSON=$rawJson, source=${configValue.source}" }
        AdDebug.log {
            "auto_interstitial_config raw JSON=$rawAutoInterstitialJson, " +
                "source=${autoInterstitialValue.source}"
        }
        AdDebug.log {
            "ad_type_config raw JSON=$rawAdTypeJson, source=${adTypeValue.source}"
        }
        val parsed = AdConfig.parse(masterValue.asBoolean(), rawJson)
        val parsedAutoInterstitial = AutoInterstitialConfig.parse(rawAutoInterstitialJson)
        val parsedAdTypes = AdTypeConfig.parse(rawAdTypeJson)
        AdDebug.log { "parsed AdConfig=$parsed" }
        _config.value = parsed ?: AdConfig.AllOff
        _autoInterstitialConfig.value = parsedAutoInterstitial ?: AutoInterstitialConfig.Off
        _adTypeConfig.value = parsedAdTypes
        AutoInterstitialManager.onConfigUpdated(_autoInterstitialConfig.value)
        appContext?.let(AdRuntime::preloadConfiguredAds)
        logEffectiveConfig()
    }

    private fun logEffectiveConfig() {
        AdDebug.log { "final effective master enabled=${_config.value.masterEnabled}" }
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
            "final auto interstitial enabled=" +
                _autoInterstitialConfig.value.enabled
        }
    }

    private fun disableAllAds() {
        _config.value = AdConfig.AllOff
        _autoInterstitialConfig.value = AutoInterstitialConfig.Off
        _adTypeConfig.value = AdTypeConfig.CurrentBehaviorFallback
        AutoInterstitialManager.onConfigUpdated(AutoInterstitialConfig.Off)
    }
}
