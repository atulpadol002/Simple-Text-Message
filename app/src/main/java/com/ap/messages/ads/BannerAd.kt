package com.ap.messages.ads

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import java.util.concurrent.atomic.AtomicBoolean

private enum class BannerLoadState {
    IDLE,
    LOADING,
    LOADED,
    DESTROYED
}

private data class BannerInstanceKey(
    val placement: AdPlacement,
    val widthDp: Int
)

private class BannerInstance(
    context: Context,
    val key: BannerInstanceKey
) {
    val adView = AdView(context)
    private val impressionRecorded = AtomicBoolean(false)
    private var loadState = BannerLoadState.IDLE
    private var visible = false
    private var shownBefore = false
    private val instanceId = System.identityHashCode(adView)

    init {
        adView.adUnitId = AdUnitIds.banner
        val bannerSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
            context,
            key.widthDp
        )
        adView.setAdSize(bannerSize)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                if (loadState == BannerLoadState.DESTROYED) return
                loadState = BannerLoadState.LOADED
                AdDebug.log {
                    "Banner onAdLoaded: placement=${key.placement} instance=$instanceId " +
                        "responseInfo=${adView.responseInfo}"
                }
                if (key.placement == AdPlacement.SCHEDULED_BANNER) {
                    AdDebug.log { "SCHEDULED_BANNER loaded=true" }
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                if (loadState == BannerLoadState.DESTROYED) return
                loadState = BannerLoadState.IDLE
                AdDebug.log {
                    "Banner onAdFailedToLoad: placement=${key.placement} instance=$instanceId " +
                        "code=${error.code} domain=${error.domain} message=${error.message} " +
                        "responseInfo=${error.responseInfo}"
                }
            }

            override fun onAdImpression() {
                if (impressionRecorded.compareAndSet(false, true) &&
                    AdSessionManager.canShowNonRewarded(AdRemoteConfigManager.config.value)
                ) {
                    AdSessionManager.recordNonRewardedShown(key.placement)
                }
            }
        }
        AdDebug.log {
            "Banner instance created: placement=${key.placement} instance=$instanceId " +
                "adUnitIdType=${if (com.ap.messages.BuildConfig.DEBUG) "TEST" else "PRODUCTION"} " +
                "adSize=${bannerSize.width}x${bannerSize.height}"
        }
        if (key.placement == AdPlacement.SCHEDULED_BANNER) {
            AdDebug.log { "SCHEDULED_BANNER created=true" }
        }
    }

    fun markShown() {
        if (loadState == BannerLoadState.DESTROYED || visible) return
        if (shownBefore) {
            AdDebug.log {
                "Banner instance reused: placement=${key.placement} instance=$instanceId"
            }
            if (key.placement == AdPlacement.SCHEDULED_BANNER) {
                AdDebug.log { "SCHEDULED_BANNER reused=true" }
            }
        }
        visible = true
        shownBefore = true
        AdDebug.log { "Banner shown: placement=${key.placement} instance=$instanceId" }
    }

    fun markHidden(reason: String) {
        if (loadState == BannerLoadState.DESTROYED || !visible) return
        visible = false
        AdDebug.log {
            "Banner hidden: placement=${key.placement} instance=$instanceId reason=$reason"
        }
    }

    fun loadIfNeeded() {
        when (loadState) {
            BannerLoadState.LOADING,
            BannerLoadState.LOADED -> {
                AdDebug.log {
                    "Banner load skipped because already loaded/loading: " +
                        "placement=${key.placement} instance=$instanceId state=$loadState"
                }
            }

            BannerLoadState.DESTROYED -> {
                AdDebug.log {
                    "Banner load skipped because instance is destroyed: " +
                        "placement=${key.placement} instance=$instanceId"
                }
            }

            BannerLoadState.IDLE -> {
                loadState = BannerLoadState.LOADING
                val size = adView.adSize
                AdDebug.log {
                    "Banner load start: placement=${key.placement} instance=$instanceId " +
                        "adUnitIdType=${if (com.ap.messages.BuildConfig.DEBUG) "TEST" else "PRODUCTION"} " +
                        "adSize=${size?.width}x${size?.height}"
                }
                adView.loadAd(AdRequest.Builder().build())
            }
        }
    }

    fun destroy(reason: String) {
        if (loadState == BannerLoadState.DESTROYED) return
        visible = false
        loadState = BannerLoadState.DESTROYED
        AdDebug.log {
            "Banner destroyed: placement=${key.placement} instance=$instanceId reason=$reason"
        }
        adView.adListener = object : AdListener() {}
        adView.destroy()
    }
}

private class BannerAdHostState(private val context: Context) {
    private val instances = mutableMapOf<BannerInstanceKey, BannerInstance>()

    fun getOrCreate(placement: AdPlacement, widthDp: Int): BannerInstance {
        val key = BannerInstanceKey(placement, widthDp)
        return instances[key] ?: BannerInstance(context, key).also { instances[key] = it }
    }

    fun destroyAll(reason: String) {
        instances.values.forEach { it.destroy(reason) }
        instances.clear()
    }
}

private class BannerLease {
    var instance by mutableStateOf<BannerInstance?>(null)
}

private val LocalBannerAdHostState = staticCompositionLocalOf<BannerAdHostState> {
    error("BannerAd must be placed below BannerAdHost")
}

@Composable
fun BannerAdHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val hostState = remember(context) { BannerAdHostState(context) }
    DisposableEffect(hostState) {
        onDispose {
            hostState.destroyAll("Activity banner host left composition")
        }
    }
    CompositionLocalProvider(LocalBannerAdHostState provides hostState, content = content)
}

@Composable
fun BannerAd(
    placement: AdPlacement,
    enabled: Boolean,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val config by AdRemoteConfigManager.config.collectAsState()
    val adsReady by AdRuntime.mobileAdsReady.collectAsState()
    val consent by AdConsentManager.canRequestAds.collectAsState()
    val adsAllowed by AdRuntime.adsAllowed.collectAsState()
    val session by AdSessionManager.snapshot.collectAsState()
    val hostState = LocalBannerAdHostState.current
    val widthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(320)
    val lease = remember(hostState, placement, widthDp) { BannerLease() }
    val sessionAllowed = AdSessionManager.canShowNonRewarded(config)
    val requestAllowed = enabled && adsAllowed && config.masterEnabled && adsReady && consent && sessionAllowed
    val shouldShow = visible && requestAllowed

    LaunchedEffect(
        lease,
        shouldShow,
        visible,
        enabled,
        config.masterEnabled,
        adsAllowed,
        adsReady,
        consent,
        session.nonRewardedShown
    ) {
        if (shouldShow) {
            val instance = lease.instance ?: hostState.getOrCreate(placement, widthDp).also {
                lease.instance = it
            }
            instance.markShown()
            instance.loadIfNeeded()
        } else {
            val reason = when {
                !visible -> "temporary UI visibility disabled"
                !enabled -> "placement disabled"
                !adsAllowed -> "premium entitlement suppresses ads"
                !config.masterEnabled -> "ads master disabled"
                !consent -> "consent does not allow ad requests"
                !adsReady -> "Mobile Ads SDK not ready"
                !sessionAllowed -> "session limit reached"
                else -> "not eligible"
            }
            lease.instance?.markHidden(reason)
            if (!visible && lease.instance == null) {
                AdDebug.log {
                    "Banner hidden: placement=$placement instance=none reason=$reason"
                }
            }
            if (!requestAllowed) {
                AdDebug.log {
                    "BannerAd blocked: placement=$placement enabled=$enabled " +
                        "master=${config.masterEnabled} consent=$consent sdkReady=$adsReady " +
                        "adsAllowed=$adsAllowed sessionAllowed=$sessionAllowed"
                }
                if (!consent) AdDebug.log { "Ad request blocked because canRequestAds=false" }
            }
        }
    }

    DisposableEffect(lease) {
        onDispose {
            lease.instance?.markHidden("screen banner host detached; retained by Activity host")
        }
    }

    val instance = lease.instance
    if (shouldShow && instance != null) {
        Box(
            modifier = modifier.fillMaxWidth().navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(factory = { instance.adView })
        }
    }
}
