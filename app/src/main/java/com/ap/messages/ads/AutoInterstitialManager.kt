package com.ap.messages.ads

import android.content.Context
import android.os.SystemClock

enum class AutoInterstitialEvent {
    HOME_TAP,
    IN_APP_TAP,
    BACK_TAP
}

object AutoInterstitialManager {
    private const val PREFERENCES_NAME = "auto_interstitial_session"
    private const val SESSION_NUMBER_KEY = "session_number"

    private var initialized = false
    private var sessionNumber = 1
    private var config = AutoInterstitialConfig.Off
    private var activeRule: AutoInterstitialRule? = null
    private val eventCounts = mutableMapOf<AutoInterstitialEvent, Long>()
    private var repeatEventCount = 0L
    private var waitingForRepeat = false
    private var accumulatedForegroundMillis = 0L
    private var foregroundStartedAtMillis: Long? = null
    private var eligible = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val previous = preferences.getInt(SESSION_NUMBER_KEY, 0).coerceAtLeast(0)
        sessionNumber = if (previous == Int.MAX_VALUE) Int.MAX_VALUE else previous + 1
        preferences.edit().putInt(SESSION_NUMBER_KEY, sessionNumber).apply()
        AdDebug.log { "Auto INT session number=$sessionNumber" }
    }

    @Synchronized
    fun onConfigUpdated(updated: AutoInterstitialConfig) {
        config = updated
        activeRule = updated.ruleFor(sessionNumber)
        eventCounts.clear()
        repeatEventCount = 0L
        waitingForRepeat = false
        eligible = false
        AdDebug.log { "Auto INT parsed config=$updated" }
        AdDebug.log {
            "Auto INT session number=$sessionNumber active rule=$activeRule " +
                "approach=${activeRule?.approach?.name?.lowercase()}"
        }
    }

    @Synchronized
    fun onForeground() {
        if (foregroundStartedAtMillis == null) {
            foregroundStartedAtMillis = SystemClock.elapsedRealtime()
        }
    }

    @Synchronized
    fun onBackground() {
        val startedAt = foregroundStartedAtMillis ?: return
        accumulatedForegroundMillis += (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        foregroundStartedAtMillis = null
    }

    @Synchronized
    fun isEnabledForCurrentSession(): Boolean =
        config.enabled && activeRule?.enabled == true

    @Synchronized
    fun currentSessionNumber(): Int = sessionNumber

    @Synchronized
    fun onEligibleEvent(
        event: AutoInterstitialEvent,
        masterEnabled: Boolean,
        sessionAllowed: Boolean
    ): Boolean {
        val rule = activeRule
        val blockedReason = when {
            !config.enabled -> "config_disabled"
            rule == null -> "active_rule_unavailable"
            !rule.enabled -> "active_rule_disabled"
            !masterEnabled -> "ads_master_disabled"
            !sessionAllowed -> "session_or_global_cap_reached"
            else -> null
        }
        if (blockedReason != null) {
            onBlocked(blockedReason)
            return false
        }
        checkNotNull(rule)

        val count = (eventCounts[event] ?: 0L) + 1L
        eventCounts[event] = count

        if (waitingForRepeat) {
            repeatEventCount += 1L
            if (repeatEventCount >= rule.repeatTap) eligible = true
        } else if (!eligible) {
            eligible = when (rule.approach) {
                AutoInterstitialApproach.TAP -> count >= thresholdFor(rule, event)
                AutoInterstitialApproach.TIME -> {
                    foregroundElapsedMillis() / 1_000L >=
                        checkNotNull(rule.timeValueSeconds)
                }
            }
        }

        val threshold = when {
            waitingForRepeat -> rule.repeatTap
            rule.approach == AutoInterstitialApproach.TIME -> rule.timeValueSeconds
            else -> thresholdFor(rule, event)
        }
        AdDebug.log {
            "Auto INT event=${event.name} count=$count threshold=$threshold eligible=$eligible"
        }
        AdDebug.log {
            "Auto INT repeat_tap=${rule.repeatTap} count=$repeatEventCount " +
                "waiting=$waitingForRepeat eligible=$eligible"
        }
        return eligible
    }

    @Synchronized
    fun onBlocked(reason: String) {
        AdDebug.log { "Auto INT blockedReason=$reason eligible=$eligible shown=false" }
    }

    @Synchronized
    fun onReadyState(ready: Boolean) {
        AdDebug.log { "Auto INT ready=$ready eligible=$eligible" }
    }

    @Synchronized
    fun onShown() {
        AdDebug.log { "Auto INT shown=true blockedReason=none" }
    }

    @Synchronized
    fun onSuccessfulImpression() {
        val rule = activeRule ?: return
        eligible = false
        waitingForRepeat = true
        repeatEventCount = 0L
        eventCounts.clear()
        AdDebug.log {
            "Auto INT successful impression repeat_tap=${rule.repeatTap} shown=true"
        }
    }

    private fun thresholdFor(
        rule: AutoInterstitialRule,
        event: AutoInterstitialEvent
    ): Long = when (event) {
        AutoInterstitialEvent.HOME_TAP -> rule.homeTap
        AutoInterstitialEvent.IN_APP_TAP -> rule.inAppTap
        AutoInterstitialEvent.BACK_TAP -> rule.backTap
    }

    private fun foregroundElapsedMillis(): Long {
        val currentForegroundMillis = foregroundStartedAtMillis?.let { startedAt ->
            (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        } ?: 0L
        return accumulatedForegroundMillis + currentForegroundMillis
    }
}
