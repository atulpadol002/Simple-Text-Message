package com.ap.simpletextmessage.ads

import org.json.JSONObject

enum class AutoInterstitialApproach {
    TAP,
    TIME
}

data class AutoInterstitialRule(
    val enabled: Boolean,
    val approach: AutoInterstitialApproach,
    val homeTap: Long,
    val inAppTap: Long,
    val backTap: Long,
    val repeatTap: Long,
    val timeValueSeconds: Long?
)

data class AutoInterstitialConfig(
    val enabled: Boolean,
    val session1: AutoInterstitialRule,
    val session2: AutoInterstitialRule,
    val session3: AutoInterstitialRule,
    val session4: AutoInterstitialRule,
    val defaultRule: AutoInterstitialRule
) {
    fun ruleFor(sessionNumber: Int): AutoInterstitialRule = when (sessionNumber) {
        1 -> session1
        2 -> session2
        3 -> session3
        4 -> session4
        else -> defaultRule
    }

    companion object {
        val Off = AutoInterstitialConfig(
            enabled = false,
            session1 = AutoInterstitialRule(false, AutoInterstitialApproach.TAP, 1L, 1L, 1L, 1L, null),
            session2 = AutoInterstitialRule(false, AutoInterstitialApproach.TAP, 1L, 1L, 1L, 1L, null),
            session3 = AutoInterstitialRule(false, AutoInterstitialApproach.TAP, 1L, 1L, 1L, 1L, null),
            session4 = AutoInterstitialRule(false, AutoInterstitialApproach.TAP, 1L, 1L, 1L, 1L, null),
            defaultRule = AutoInterstitialRule(false, AutoInterstitialApproach.TAP, 1L, 1L, 1L, 1L, null)
        )

        val Defaults = AutoInterstitialConfig(
            enabled = true,
            session1 = defaultTapRule(),
            session2 = defaultTapRule(),
            session3 = defaultTimeRule(30L),
            session4 = defaultTimeRule(45L),
            defaultRule = defaultTapRule()
        )

        fun parse(json: String): AutoInterstitialConfig? = runCatching {
            val root = JSONObject(json)
            AutoInterstitialConfig(
                enabled = root.getBoolean("enabled"),
                session1 = root.requiredRule("session1"),
                session2 = root.requiredRule("session2"),
                session3 = root.requiredRule("session3"),
                session4 = root.requiredRule("session4"),
                defaultRule = root.requiredRule("default")
            )
        }.onFailure { error ->
            AdDebug.log {
                "Auto INT config parse rejected JSON: " +
                    "${error.javaClass.simpleName}: ${error.message}"
            }
        }.getOrNull()
    }
}

private fun defaultTapRule() = AutoInterstitialRule(
    enabled = true,
    approach = AutoInterstitialApproach.TAP,
    homeTap = 2L,
    inAppTap = 2L,
    backTap = 2L,
    repeatTap = 4L,
    timeValueSeconds = null
)

private fun defaultTimeRule(seconds: Long) = AutoInterstitialRule(
    enabled = true,
    approach = AutoInterstitialApproach.TIME,
    homeTap = 2L,
    inAppTap = 2L,
    backTap = 2L,
    repeatTap = 4L,
    timeValueSeconds = seconds
)

private fun JSONObject.requiredRule(name: String): AutoInterstitialRule {
    val rule = getJSONObject(name)
    val approach = when (rule.getString("approach")) {
        "tap" -> AutoInterstitialApproach.TAP
        "time" -> AutoInterstitialApproach.TIME
        else -> error("$name.approach must be tap or time")
    }
    // Legacy value/repeat configs remain valid and map the old shared tap threshold
    // to each explicitly classified event.
    val legacyValue = rule.optPositiveLong("value")
    val homeTap = rule.optPositiveLong("home_tap") ?: legacyValue
    val inAppTap = rule.optPositiveLong("is_tap") ?: legacyValue
    val backTap = rule.optPositiveLong("back_tap") ?: legacyValue
    val repeatTap = rule.optPositiveLong("repeat_tap") ?:
        rule.optPositiveLong("repeat")
    val timeValue = rule.optPositiveLong("value")
    if (rule.getBoolean("enabled")) {
        when (approach) {
            AutoInterstitialApproach.TAP -> require(
                homeTap != null && inAppTap != null && backTap != null
            ) { "$name tap thresholds must be positive" }
            AutoInterstitialApproach.TIME -> require(timeValue != null) {
                "$name.value must be positive for time approach"
            }
        }
        require(repeatTap != null) { "$name.repeat_tap must be positive" }
    }
    return AutoInterstitialRule(
        enabled = rule.getBoolean("enabled"),
        approach = approach,
        homeTap = homeTap ?: 1L,
        inAppTap = inAppTap ?: 1L,
        backTap = backTap ?: 1L,
        repeatTap = repeatTap ?: 1L,
        timeValueSeconds = timeValue
    )
}

private fun JSONObject.optPositiveLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return getLong(name).also { require(it > 0L) { "$name must be positive" } }
}
