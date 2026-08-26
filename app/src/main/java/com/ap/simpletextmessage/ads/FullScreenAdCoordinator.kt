package com.ap.simpletextmessage.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FullScreenAdType {
    UMP,
    REWARDED,
    AUTO_INTERSTITIAL,
    NORMAL_INTERSTITIAL,
    ONBOARDING_INTERSTITIAL,
    APP_OPEN,
    BILLING,
    PAYWALL_POPUP
}

object FullScreenAdCoordinator {
    private var active: FullScreenAdType? = null
    private val _activeType = MutableStateFlow<FullScreenAdType?>(null)
    val activeTypeFlow: StateFlow<FullScreenAdType?> = _activeType.asStateFlow()

    @Synchronized
    fun tryAcquire(type: FullScreenAdType): Boolean {
        if (active != null) return false
        active = type
        _activeType.value = type
        return true
    }

    @Synchronized
    fun release(type: FullScreenAdType) {
        if (active == type) {
            active = null
            _activeType.value = null
        }
    }

    @Synchronized
    fun activeType(): FullScreenAdType? = active
}
