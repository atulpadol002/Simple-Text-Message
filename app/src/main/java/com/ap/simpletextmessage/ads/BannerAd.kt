package com.ap.simpletextmessage.ads

import android.content.Context
import android.widget.FrameLayout
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
import androidx.compose.runtime.rememberUpdatedState
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
    val widthDp: Int,
    val configurationRevision: Long
)

private class BannerInstance(
    private val context: Context,
    val key: BannerInstanceKey
) {
    val view = FrameLayout(context)
    private val impressionRecorded = AtomicBoolean(false)
    private val bannerSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
        context,
        key.widthDp
    )
    private var adView: AdView? = null
    private var loadState = BannerLoadState.IDLE
    private var loadSource = AdLoadSource.PRIMARY
    private var visible = false
    private var shownBefore = false
    private val instanceId = System.identityHashCode(view)
    var isLoaded by mutableStateOf(false)
        private set

    init {
        AdDebug.log {
            "Banner instance created: placement=${key.placement} instance=$instanceId " +
                "adUnitIdType=${if (AdRemoteConfigManager.testMode.value) "TEST" else "PRODUCTION"} " +
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
                impressionRecorded.set(false)
                load(AdLoadSource.PRIMARY)
            }
        }
    }

    private fun load(source: AdLoadSource) {
        if (loadState == BannerLoadState.DESTROYED) return
        if (!AdRuntime.canLoadAds("BANNER", source)) {
            loadState = BannerLoadState.IDLE
            isLoaded = false
            adView?.let { current ->
                current.adListener = object : AdListener() {}
                view.removeView(current)
                current.destroy()
            }
            adView = null
            return
        }
        loadState = BannerLoadState.LOADING
        loadSource = source
        isLoaded = false

        adView?.let { previous ->
            previous.adListener = object : AdListener() {}
            view.removeView(previous)
            previous.destroy()
        }

        val next = AdView(context).apply {
            adUnitId = AdUnitIds.banner(source)
            setAdSize(bannerSize)
        }
        adView = next
        view.addView(next)
        AdDebug.log { "AdLoad format=BANNER source=$source started" }
        AdDebug.log {
            "Banner load start: placement=${key.placement} instance=$instanceId " +
                "source=$source adSize=${bannerSize.width}x${bannerSize.height}"
        }
        next.adListener = object : AdListener() {
            override fun onAdLoaded() {
                if (loadState == BannerLoadState.DESTROYED || adView !== next || loadSource != source) {
                    return
                }
                loadState = BannerLoadState.LOADED
                isLoaded = true
                AdDebug.log { "AdLoad format=BANNER source=$source loaded" }
                AdDebug.log {
                    "Banner onAdLoaded: placement=${key.placement} instance=$instanceId " +
                        "source=$source responseInfo=${next.responseInfo}"
                }
                if (key.placement == AdPlacement.SCHEDULED_BANNER) {
                    AdDebug.log { "SCHEDULED_BANNER loaded=true" }
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                if (loadState == BannerLoadState.DESTROYED || adView !== next || loadSource != source) {
                    return
                }
                isLoaded = false
                AdDebug.log {
                    "AdLoad format=BANNER source=$source failed code=${error.code}"
                }
                AdDebug.log {
                    "Banner onAdFailedToLoad: placement=${key.placement} instance=$instanceId " +
                        "source=$source code=${error.code} domain=${error.domain} " +
                        "responseInfo=${error.responseInfo}"
                }
                if (
                    source == AdLoadSource.PRIMARY &&
                    AdUnitIds.hasDistinctBackup(AdUnitIds.banner, AdUnitIds.bannerBackup)
                ) {
                    load(AdLoadSource.BACKUP)
                } else {
                    loadState = BannerLoadState.IDLE
                    next.adListener = object : AdListener() {}
                    view.removeView(next)
                    next.destroy()
                    if (adView === next) adView = null
                }
            }

            override fun onAdImpression() {
                if (impressionRecorded.compareAndSet(false, true) &&
                    AdSessionManager.canShowAd(AdRemoteConfigManager.config.value)
                ) {
                    AdSessionManager.recordNonRewardedShown(key.placement)
                }
            }
        }
        next.loadAd(AdRequest.Builder().build())
    }

    fun destroy(reason: String) {
        if (loadState == BannerLoadState.DESTROYED) return
        visible = false
        isLoaded = false
        loadState = BannerLoadState.DESTROYED
        AdDebug.log {
            "Banner destroyed: placement=${key.placement} instance=$instanceId reason=$reason"
        }
        adView?.let { current ->
            current.adListener = object : AdListener() {}
            view.removeView(current)
            current.destroy()
        }
        adView = null
    }
}

private class BannerAdHostState(private val context: Context) {
    private val instances = mutableMapOf<BannerInstanceKey, BannerInstance>()

    fun getOrCreate(
        placement: AdPlacement,
        widthDp: Int,
        configurationRevision: Long
    ): BannerInstance {
        val key = BannerInstanceKey(placement, widthDp, configurationRevision)
        return instances[key] ?: BannerInstance(context, key).also { instances[key] = it }
    }

    fun destroy(instance: BannerInstance, reason: String) {
        instances.remove(instance.key)
        instance.destroy(reason)
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
    modifier: Modifier = Modifier,
    applyNavigationBarsPadding: Boolean = true,
    onLoaded: () -> Unit = {}
) {
    val config by AdRemoteConfigManager.config.collectAsState()
    val adsReady by AdRuntime.mobileAdsReady.collectAsState()
    val consent by AdConsentManager.canRequestAds.collectAsState()
    val adsAllowed by AdRuntime.adsAllowed.collectAsState()
    val session by AdSessionManager.snapshot.collectAsState()
    val configurationRevision by AdRemoteConfigManager.configurationRevision.collectAsState()
    val hostState = LocalBannerAdHostState.current
    val currentOnLoaded by rememberUpdatedState(onLoaded)
    val widthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(320)
    val lease = remember(hostState, placement, widthDp, configurationRevision) { BannerLease() }
    val sessionAllowed = AdSessionManager.canShowAd(config)
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
        session.totalAdsShown,
        configurationRevision
    ) {
        if (shouldShow) {
            val instance = lease.instance ?: hostState.getOrCreate(
                placement,
                widthDp,
                configurationRevision
            ).also {
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
            if (!requestAllowed) {
                lease.instance?.let { instance -> hostState.destroy(instance, reason) }
                lease.instance = null
            }
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
            lease.instance?.let { instance ->
                hostState.destroy(instance, "screen banner host detached")
            }
            lease.instance = null
        }
    }

    val instance = lease.instance
    LaunchedEffect(instance, instance?.isLoaded) {
        if (instance?.isLoaded == true) currentOnLoaded()
    }
    if (shouldShow && instance != null && instance.isLoaded) {
        Box(
            modifier = modifier.fillMaxWidth().let {
                if (applyNavigationBarsPadding) it.navigationBarsPadding() else it
            },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(factory = { instance.view })
        }
    }
}
