package com.ap.messages.ads

import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.ap.messages.BuildConfig
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdConsentManager {
    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()
    private val _consentStatus =
        MutableStateFlow(ConsentInformation.ConsentStatus.UNKNOWN)
    val consentStatus: StateFlow<Int> = _consentStatus.asStateFlow()
    private val _privacyOptionsRequirementStatus = MutableStateFlow(
        ConsentInformation.PrivacyOptionsRequirementStatus.UNKNOWN
    )
    val privacyOptionsRequirementStatus:
        StateFlow<ConsentInformation.PrivacyOptionsRequirementStatus> =
        _privacyOptionsRequirementStatus.asStateFlow()
    private var requestStarted = false
    private var requestCompleted = false
    private val completionCallbacks = mutableListOf<(Boolean) -> Unit>()

    fun gatherConsent(activity: Activity, onComplete: (Boolean) -> Unit) {
        val requestAction = synchronized(this) {
            when {
                requestCompleted -> REQUEST_ALREADY_COMPLETED
                requestStarted -> {
                    completionCallbacks += onComplete
                    REQUEST_WAITING
                }
                else -> {
                    completionCallbacks += onComplete
                    requestStarted = true
                    REQUEST_START
                }
            }
        }
        if (requestAction == REQUEST_ALREADY_COMPLETED) {
            AdDebug.log { "UMP request already completed; canRequestAds=${_canRequestAds.value}" }
            onComplete(_canRequestAds.value)
            return
        }
        if (requestAction == REQUEST_WAITING) {
            AdDebug.log { "UMP request already started; canRequestAds=${_canRequestAds.value}" }
            return
        }
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        updateConsentMetadata(consentInformation)
        AdDebug.log {
            "ConsentStartup returningUser=" +
                (consentInformation.consentStatus != ConsentInformation.ConsentStatus.UNKNOWN)
        }
        if (!isActivityResumed(activity)) {
            AdDebug.log { "ConsentStartup formShown=false" }
            AdDebug.log { "ConsentStartup blockedReason=activity_not_resumed" }
            finishConsentRequest(false)
            return
        }
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.UMP)) {
            AdDebug.log { "ConsentStartup formShown=false" }
            AdDebug.log { "ConsentStartup blockedReason=fullscreen_in_progress" }
            finishConsentRequest(false)
            return
        }
        logConsentState(consentInformation, "before update")
        consentInformation.requestConsentInfoUpdate(
            activity,
            buildConsentRequestParameters(activity),
            {
                logConsentState(consentInformation, "after update")
                updateConsentMetadata(consentInformation)
                val formRequired =
                    consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
                AdDebug.log {
                    "UMP consent form required=$formRequired, " +
                        "formAvailable=${consentInformation.isConsentFormAvailable}"
                }
                AdDebug.log { "ConsentStartup formRequired=$formRequired" }
                if (!formRequired) {
                    AdDebug.log { "ConsentStartup formShown=false" }
                    AdDebug.log { "ConsentStartup blockedReason=none" }
                    completeConsent(consentInformation)
                    return@requestConsentInfoUpdate
                }
                if (!isActivityResumed(activity)) {
                    _canRequestAds.value = false
                    FullScreenAdCoordinator.release(FullScreenAdType.UMP)
                    AdDebug.log { "ConsentStartup formShown=false" }
                    AdDebug.log { "ConsentStartup blockedReason=activity_not_resumed" }
                    finishConsentRequest(false)
                    return@requestConsentInfoUpdate
                }
                AdRuntime.suppressNextAppOpen()
                AdDebug.log { "ConsentStartup formShown=true" }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    FullScreenAdCoordinator.release(FullScreenAdType.UMP)
                    AdDebug.log {
                        "UMP consent form dismissed: errorCode=${formError?.errorCode}, " +
                            "message=${formError?.message}"
                    }
                    AdDebug.log {
                        "ConsentStartup blockedReason=" +
                            (formError?.let { "form_error_${it.errorCode}" } ?: "none")
                    }
                    logConsentState(consentInformation, "after form")
                    completeConsent(consentInformation)
                }
            },
            { error ->
                // Never request ads using stale consent after a failed launch-time update.
                AdDebug.log {
                    "UMP consent info update failed: errorCode=${error.errorCode}, message=${error.message}"
                }
                _canRequestAds.value = false
                FullScreenAdCoordinator.release(FullScreenAdType.UMP)
                AdDebug.log { "Ad requests blocked: canRequestAds=false" }
                AdDebug.log { "ConsentStartup formShown=false" }
                AdDebug.log { "ConsentStartup blockedReason=consent_update_error_${error.errorCode}" }
                finishConsentRequest(false)
            }
        )
    }

    internal fun resetForDebugTesting(context: Context) {
        if (!BuildConfig.DEBUG) return
        UserMessagingPlatform.getConsentInformation(context).reset()
        synchronized(this) {
            requestStarted = false
            requestCompleted = false
            completionCallbacks.clear()
        }
        _canRequestAds.value = false
        _consentStatus.value = ConsentInformation.ConsentStatus.UNKNOWN
        _privacyOptionsRequirementStatus.value =
            ConsentInformation.PrivacyOptionsRequirementStatus.UNKNOWN
        AdDebug.log { "UMP debug reset completed; canRequestAds=false" }
    }

    fun showPrivacyOptions(activity: Activity) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val requirementStatus = consentInformation.privacyOptionsRequirementStatus
        updateConsentMetadata(consentInformation)
        if (requirementStatus != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            AdDebug.log { "PrivacyOptions formShown=false" }
            return
        }
        if (!isActivityResumed(activity)) {
            AdDebug.log { "PrivacyOptions formShown=false" }
            AdDebug.log { "PrivacyOptions error=activity_not_safe" }
            return
        }
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.UMP)) {
            AdDebug.log { "UMP privacy UI blocked by another full-screen presentation" }
            AdDebug.log { "PrivacyOptions formShown=false" }
            AdDebug.log { "PrivacyOptions error=fullscreen_in_progress" }
            return
        }
        AdRuntime.suppressNextAppOpen()
        AdDebug.log { "PrivacyOptions formShown=true" }
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            FullScreenAdCoordinator.release(FullScreenAdType.UMP)
            AdDebug.log {
                "UMP privacy options form closed: errorCode=${error?.errorCode}, message=${error?.message}"
            }
            AdDebug.log { "PrivacyOptions dismissed=true" }
            AdDebug.log {
                "PrivacyOptions error=" +
                    (error?.let { "${it.errorCode}:${it.message}" } ?: "none")
            }
            refreshConsentState(consentInformation)
            AdDebug.log { "PrivacyOptions updatedCanRequestAds=${_canRequestAds.value}" }
            AdDebug.log {
                "PrivacyOptions updatedRequirementStatus=" +
                    _privacyOptionsRequirementStatus.value
            }
            logConsentState(consentInformation, "after privacy options")
            AdRuntime.onConsentStateChanged(activity.applicationContext)
        }
    }

    private fun completeConsent(
        consentInformation: ConsentInformation
    ) {
        FullScreenAdCoordinator.release(FullScreenAdType.UMP)
        refreshConsentState(consentInformation)
        val allowed = _canRequestAds.value
        AdDebug.log { "UMP canRequestAds=$allowed" }
        AdDebug.log { "ConsentStartup canRequestAds=$allowed" }
        if (!allowed) AdDebug.log { "Ad requests blocked: canRequestAds=false" }
        finishConsentRequest(allowed)
    }

    private fun finishConsentRequest(allowed: Boolean) {
        val callbacks = synchronized(this) {
            requestCompleted = true
            val pendingCallbacks = completionCallbacks.toList()
            completionCallbacks.clear()
            pendingCallbacks
        }
        callbacks.forEach { it(allowed) }
    }

    private fun updateConsentMetadata(consentInformation: ConsentInformation) {
        _consentStatus.value = consentInformation.consentStatus
        val requirementStatus = consentInformation.privacyOptionsRequirementStatus
        _privacyOptionsRequirementStatus.value = requirementStatus
        val revokeVisible =
            requirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        AdDebug.log { "PrivacyDrawer requirementStatus=$requirementStatus" }
        AdDebug.log { "PrivacyDrawer revokeVisible=$revokeVisible" }
    }

    private fun refreshConsentState(consentInformation: ConsentInformation) {
        updateConsentMetadata(consentInformation)
        _canRequestAds.value = consentInformation.canRequestAds()
    }

    private fun buildConsentRequestParameters(context: Context): ConsentRequestParameters {
        val parameters = ConsentRequestParameters.Builder()
        if (!BuildConfig.DEBUG) return parameters.build()

        val debugGeography = when (BuildConfig.UMP_DEBUG_GEOGRAPHY) {
            "EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
            "REGULATED_US_STATE" ->
                ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
            "OTHER" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
            else -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
        }
        if (debugGeography == ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED) {
            AdDebug.log { "UMP debug geography=DISABLED" }
            return parameters.build()
        }

        val testDeviceHash = BuildConfig.UMP_TEST_DEVICE_HASH
        val debugSettings = ConsentDebugSettings.Builder(context)
            .setDebugGeography(debugGeography)
            .apply {
                if (testDeviceHash.isNotBlank()) addTestDeviceHashedId(testDeviceHash)
            }
            .build()
        AdDebug.log {
            "UMP debug geography=${BuildConfig.UMP_DEBUG_GEOGRAPHY} " +
                "testDeviceHashConfigured=${testDeviceHash.isNotBlank()}"
        }
        return parameters.setConsentDebugSettings(debugSettings).build()
    }

    private fun logConsentState(consentInformation: ConsentInformation, stage: String) {
        AdDebug.log {
            "UMP $stage: consentStatus=${consentInformation.consentStatus}, " +
                "privacyOptionsRequirementStatus=${consentInformation.privacyOptionsRequirementStatus}, " +
                "canRequestAds=${consentInformation.canRequestAds()}"
        }
    }

    private fun isActivityResumed(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val lifecycleOwner = activity as? LifecycleOwner ?: return true
        return lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    private const val REQUEST_ALREADY_COMPLETED = 0
    private const val REQUEST_WAITING = 1
    private const val REQUEST_START = 2
}
