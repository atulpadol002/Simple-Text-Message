package com.ap.messages.ads

import android.app.Activity
import com.ap.messages.BuildConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdConsentManager {
    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()
    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()
    private var requestStarted = false

    @Synchronized
    fun gatherConsent(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (requestStarted) {
            AdDebug.log { "UMP request already started; canRequestAds=${_canRequestAds.value}" }
            onComplete(_canRequestAds.value)
            return
        }
        requestStarted = true
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        logConsentState(consentInformation, "before update")
        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                logConsentState(consentInformation, "after update")
                _privacyOptionsRequired.value =
                    consentInformation.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                val formRequired =
                    consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
                AdDebug.log {
                    "UMP consent form required=$formRequired, " +
                        "formAvailable=${consentInformation.isConsentFormAvailable}"
                }
                if (!BuildConfig.DEBUG) {
                    if (!formRequired) {
                        completeConsent(consentInformation, onComplete)
                        return@requestConsentInfoUpdate
                    }
                    if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.UMP)) {
                        _canRequestAds.value = false
                        AdDebug.log { "UMP UI blocked by another full-screen presentation" }
                        onComplete(false)
                        return@requestConsentInfoUpdate
                    }
                    AdRuntime.suppressNextAppOpen()
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                        FullScreenAdCoordinator.release(FullScreenAdType.UMP)
                        completeConsent(consentInformation, onComplete)
                    }
                    return@requestConsentInfoUpdate
                }
                if (!formRequired) {
                    completeConsent(consentInformation, onComplete)
                    return@requestConsentInfoUpdate
                }
                AdDebug.log { "UMP consent form load start" }
                UserMessagingPlatform.loadConsentForm(
                    activity,
                    { form ->
                        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.UMP)) {
                            _canRequestAds.value = false
                            AdDebug.log { "UMP UI blocked by another full-screen presentation" }
                            onComplete(false)
                            return@loadConsentForm
                        }
                        AdRuntime.suppressNextAppOpen()
                        AdDebug.log { "UMP consent form loaded=true; shown=true" }
                        form.show(activity) { formError ->
                            FullScreenAdCoordinator.release(FullScreenAdType.UMP)
                            AdDebug.log {
                                "UMP consent form dismissed: errorCode=${formError?.errorCode}, " +
                                    "message=${formError?.message}"
                            }
                            logConsentState(consentInformation, "after form")
                            completeConsent(consentInformation, onComplete)
                        }
                    },
                    { formError ->
                        AdDebug.log {
                            "UMP consent form loaded=false; shown=false; " +
                                "errorCode=${formError.errorCode}, message=${formError.message}"
                        }
                        completeConsent(consentInformation, onComplete)
                    }
                )
            },
            { error ->
                // Never request ads using stale consent after a failed launch-time update.
                AdDebug.log {
                    "UMP consent info update failed: errorCode=${error.errorCode}, message=${error.message}"
                }
                _canRequestAds.value = false
                AdDebug.log { "Ad requests blocked: canRequestAds=false" }
                onComplete(false)
            }
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        if (!FullScreenAdCoordinator.tryAcquire(FullScreenAdType.UMP)) {
            AdDebug.log { "UMP privacy UI blocked by another full-screen presentation" }
            return
        }
        AdRuntime.suppressNextAppOpen()
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            FullScreenAdCoordinator.release(FullScreenAdType.UMP)
            AdDebug.log {
                "UMP privacy options form closed: errorCode=${error?.errorCode}, message=${error?.message}"
            }
            val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
            _canRequestAds.value = consentInformation.canRequestAds()
            _privacyOptionsRequired.value =
                consentInformation.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            logConsentState(consentInformation, "after privacy options")
        }
    }

    private fun completeConsent(
        consentInformation: ConsentInformation,
        onComplete: (Boolean) -> Unit
    ) {
        val allowed = consentInformation.canRequestAds()
        _canRequestAds.value = allowed
        AdDebug.log { "UMP canRequestAds=$allowed" }
        if (!allowed) AdDebug.log { "Ad requests blocked: canRequestAds=false" }
        onComplete(allowed)
    }

    private fun logConsentState(consentInformation: ConsentInformation, stage: String) {
        AdDebug.log {
            "UMP $stage: consentStatus=${consentInformation.consentStatus}, " +
                "privacyOptionsRequirementStatus=${consentInformation.privacyOptionsRequirementStatus}, " +
                "canRequestAds=${consentInformation.canRequestAds()}"
        }
    }
}
