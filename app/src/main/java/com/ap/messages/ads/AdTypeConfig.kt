package com.ap.messages.ads

import org.json.JSONObject

enum class AdType(val remoteValue: String) {
    BANNER("banner"),
    NATIVE("native"),
    INTERSTITIAL("interstitial"),
    REWARDED("rewarded"),
    APP_OPEN("app_open"),
    NONE("none");

    companion object {
        fun fromRemoteValue(value: String): AdType? = entries.firstOrNull {
            it.remoteValue == value
        }
    }
}

enum class AdTypePlacement(
    val remoteKey: String,
    val compatibleTypes: Set<AdType>,
    val currentFallback: AdType
) {
    HOME("home", setOf(AdType.BANNER, AdType.NATIVE, AdType.NONE), AdType.BANNER),
    HOME_INLINE("homeInline", setOf(AdType.NATIVE, AdType.NONE), AdType.NATIVE),
    ARCHIVE("archive", setOf(AdType.NATIVE, AdType.BANNER, AdType.NONE), AdType.NATIVE),
    BLOCKED("blocked", setOf(AdType.BANNER, AdType.NATIVE, AdType.NONE), AdType.BANNER),
    STARRED("starred", setOf(AdType.BANNER, AdType.NATIVE, AdType.NONE), AdType.BANNER),
    SCHEDULED("scheduled", setOf(AdType.BANNER, AdType.NONE), AdType.NONE),
    SERVICE_CHAT("serviceChat", setOf(AdType.NATIVE, AdType.NONE), AdType.NONE),
    CHAT("chat", setOf(AdType.NONE), AdType.NONE),
    RATE_US("rateUs", setOf(AdType.NONE), AdType.NONE),
    EXIT("exit", setOf(AdType.NONE), AdType.NONE),
    NORMAL_INTERSTITIAL(
        "normalInterstitial",
        setOf(AdType.INTERSTITIAL, AdType.NONE),
        AdType.INTERSTITIAL
    ),
    AUTO_INTERSTITIAL(
        "autoInterstitial",
        setOf(AdType.INTERSTITIAL, AdType.NONE),
        AdType.INTERSTITIAL
    ),
    ONBOARDING(
        "onboarding",
        setOf(AdType.INTERSTITIAL, AdType.NONE),
        AdType.INTERSTITIAL
    ),
    APP_OPEN("appOpen", setOf(AdType.APP_OPEN, AdType.NONE), AdType.APP_OPEN),
    RESTORE("restore", setOf(AdType.REWARDED, AdType.NONE), AdType.REWARDED),
    DELETE_FOREVER(
        "deleteForever",
        setOf(AdType.REWARDED, AdType.NONE),
        AdType.REWARDED
    )
}

class AdTypeConfig private constructor(
    private val placementTypes: Map<AdTypePlacement, AdType>
) {
    operator fun get(placement: AdTypePlacement): AdType =
        placementTypes.getValue(placement)

    fun allows(placement: AdTypePlacement, requiredType: AdType): Boolean =
        this[placement] == requiredType

    fun logBlocked(placement: AdTypePlacement) {
        val reason = if (this[placement] == AdType.NONE) {
            "ad_type_none"
        } else {
            "incompatible_ad_type"
        }
        AdDebug.log { "placement=${placement.remoteKey} reason=$reason" }
    }

    companion object {
        val CurrentBehaviorFallback = AdTypeConfig(
            buildMap {
                for (placement in AdTypePlacement.entries) {
                    put(placement, placement.currentFallback)
                }
            }
        )

        fun parse(json: String): AdTypeConfig {
            val root = runCatching { JSONObject(json) }.getOrElse { error ->
                AdDebug.log {
                    "AdType config fallback reason=malformed_json " +
                        "error=${error.javaClass.simpleName}"
                }
                logParsed(CurrentBehaviorFallback)
                return CurrentBehaviorFallback
            }
            if (root.length() == 0) {
                AdDebug.log {
                    "AdType config fallback reason=missing_parameter " +
                        "behavior=current_existing_placement_types"
                }
                logParsed(CurrentBehaviorFallback)
                return CurrentBehaviorFallback
            }

            val parsed = mutableMapOf<AdTypePlacement, AdType>()
            for (placement in AdTypePlacement.entries) {
                parsed[placement] = parsePlacement(root, placement)
            }
            val config = AdTypeConfig(parsed)
            logParsed(config)
            return config
        }

        private fun parsePlacement(
            root: JSONObject,
            placement: AdTypePlacement
        ): AdType {
            if (!root.has(placement.remoteKey) || root.isNull(placement.remoteKey)) {
                AdDebug.log {
                    "placement=${placement.remoteKey} reason=missing_value " +
                        "fallback=${placement.currentFallback.remoteValue}"
                }
                return placement.currentFallback
            }
            val raw = root.get(placement.remoteKey)
            if (raw !is String) {
                AdDebug.log {
                    "placement=${placement.remoteKey} reason=invalid_ad_type " +
                        "value_type=${raw.javaClass.simpleName}"
                }
                return AdType.NONE
            }
            val type = AdType.fromRemoteValue(raw)
            if (type == null) {
                AdDebug.log {
                    "placement=${placement.remoteKey} reason=invalid_ad_type value=$raw"
                }
                return AdType.NONE
            }
            if (type !in placement.compatibleTypes) {
                AdDebug.log {
                    "placement=${placement.remoteKey} reason=incompatible_ad_type " +
                        "value=${type.remoteValue}"
                }
                return AdType.NONE
            }
            if (type == AdType.NONE) {
                AdDebug.log { "placement=${placement.remoteKey} reason=ad_type_none" }
            }
            return type
        }

        private fun logParsed(config: AdTypeConfig) {
            AdDebug.log { "AdType config parsed:" }
            AdTypePlacement.entries.forEach { placement ->
                AdDebug.log {
                    "${placement.remoteKey}=${config[placement].remoteValue}"
                }
            }
        }
    }
}
