package com.ap.simpletextmessage.ads

import android.graphics.Color
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import java.util.concurrent.atomic.AtomicBoolean

private data class NativeInstanceKey(
    val placement: AdPlacement,
    val cacheKey: String,
    val configurationRevision: Long
)

private class NativeAdInstance(
    private val context: Context,
    val key: NativeInstanceKey
) {
    var nativeAd by mutableStateOf<NativeAd?>(null)
        private set
    private var loading = false
    private var destroyed = false
    private val impressionRecorded = AtomicBoolean(false)

    fun loadIfNeeded() {
        if (loading || nativeAd != null || destroyed) return
        load(AdLoadSource.PRIMARY)
    }

    private fun load(source: AdLoadSource) {
        if (destroyed || key.configurationRevision != AdRemoteConfigManager.configurationRevision.value) return
        if (!AdRuntime.canLoadAds("NATIVE", source)) return
        loading = true
        AdDebug.log { "AdLoad format=NATIVE placement=${key.placement} cacheKey=${key.cacheKey} source=$source started" }
        AdLoader.Builder(context, AdUnitIds.native(source))
            .forNativeAd { loaded ->
                loading = false
                if (destroyed ||
                    key.configurationRevision != AdRemoteConfigManager.configurationRevision.value ||
                    !AdRemoteConfigManager.config.value.masterEnabled
                ) {
                    loaded.destroy()
                } else {
                    nativeAd?.destroy()
                    nativeAd = loaded
                    AdDebug.log { "AdLoad format=NATIVE placement=${key.placement} cacheKey=${key.cacheKey} source=$source loaded" }
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdImpression() {
                    if (impressionRecorded.compareAndSet(false, true) &&
                        AdSessionManager.canShowAd(AdRemoteConfigManager.config.value)
                    ) {
                        AdSessionManager.recordNonRewardedShown(key.placement)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    if (destroyed || key.configurationRevision != AdRemoteConfigManager.configurationRevision.value) return
                    AdDebug.log { "AdLoad format=NATIVE placement=${key.placement} source=$source failed code=${error.code}" }
                    if (source == AdLoadSource.PRIMARY &&
                        AdUnitIds.hasDistinctBackup(AdUnitIds.native, AdUnitIds.nativeBackup)
                    ) {
                        load(AdLoadSource.BACKUP)
                    }
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        loading = false
        nativeAd?.destroy()
        nativeAd = null
    }
}

private class NativeAdHostState(private val context: Context) {
    private val instances = mutableMapOf<NativeInstanceKey, NativeAdInstance>()

    fun getOrCreate(key: NativeInstanceKey): NativeAdInstance =
        instances[key] ?: NativeAdInstance(context, key).also { instances[key] = it }

    fun retainRevision(revision: Long) {
        val stale = instances.filterKeys { it.configurationRevision != revision }
        stale.values.forEach(NativeAdInstance::destroy)
        instances.keys.removeAll(stale.keys)
    }

    fun destroyAll() {
        instances.values.forEach(NativeAdInstance::destroy)
        instances.clear()
    }
}

private val LocalNativeAdHostState = staticCompositionLocalOf<NativeAdHostState> {
    error("NativeAdCard must be placed below NativeAdHost")
}

@Composable
fun NativeAdHost(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val hostState = remember(context) { NativeAdHostState(context) }
    val revision by AdRemoteConfigManager.configurationRevision.collectAsState()
    val config by AdRemoteConfigManager.config.collectAsState()
    val adsAllowed by AdRuntime.adsAllowed.collectAsState()
    LaunchedEffect(revision) { hostState.retainRevision(revision) }
    LaunchedEffect(config.masterEnabled, adsAllowed) {
        if (!config.masterEnabled || !adsAllowed) hostState.destroyAll()
    }
    DisposableEffect(hostState) {
        onDispose { hostState.destroyAll() }
    }
    CompositionLocalProvider(LocalNativeAdHostState provides hostState, content = content)
}

@Composable
fun NativeAdCard(
    placement: AdPlacement,
    enabled: Boolean,
    maxPerSession: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    cacheKey: String = placement.name,
    onLoaded: () -> Unit = {}
) {
    val config by AdRemoteConfigManager.config.collectAsState()
    val adsReady by AdRuntime.mobileAdsReady.collectAsState()
    val consent by AdConsentManager.canRequestAds.collectAsState()
    val adsAllowed by AdRuntime.adsAllowed.collectAsState()
    val session by AdSessionManager.snapshot.collectAsState()
    val configurationRevision by AdRemoteConfigManager.configurationRevision.collectAsState()
    val requestAllowed = enabled && maxPerSession > 0 && session.count(placement) < maxPerSession &&
        adsAllowed && config.masterEnabled && adsReady && consent &&
        AdSessionManager.canShowAd(config)
    if (!requestAllowed
    ) return

    val currentOnLoaded by rememberUpdatedState(onLoaded)
    val hostState = LocalNativeAdHostState.current
    val instance = remember(hostState, placement, cacheKey, configurationRevision) {
        hostState.getOrCreate(NativeInstanceKey(placement, cacheKey, configurationRevision))
    }
    LaunchedEffect(instance) { instance.loadIfNeeded() }
    val ad = instance.nativeAd ?: return
    LaunchedEffect(ad) { currentOnLoaded() }
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Surface(
        modifier = modifier.fillMaxWidth().padding(
            horizontal = 12.dp,
            vertical = if (compact) 4.dp else 6.dp
        ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().padding(if (compact) 6.dp else 10.dp),
            factory = {
                if (compact) {
                    buildCompactNativeAdView(it, ad, onSurface, onSurfaceVariant)
                } else {
                    buildNativeAdView(it, ad, onSurface, onSurfaceVariant)
                }
            },
            update = { view -> view.setNativeAd(ad) },
            onRelease = { it.destroy() }
        )
    }
}

private fun buildCompactNativeAdView(
    context: android.content.Context,
    ad: NativeAd,
    onSurface: Int,
    onSurfaceVariant: Int
): NativeAdView {
    val nativeView = NativeAdView(context)
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
        setBackgroundColor(Color.TRANSPARENT)
    }
    val attribution = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    attribution.addView(TextView(context).apply {
        text = "Ad"
        textSize = 11f
        setTextColor(onSurfaceVariant)
    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    val adChoices = AdChoicesView(context)
    attribution.addView(adChoices)
    root.addView(attribution)

    val content = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val icon = ImageView(context).apply {
        ad.icon?.drawable?.let(::setImageDrawable)
        visibility = if (ad.icon == null) View.GONE else View.VISIBLE
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    content.addView(
        icon,
        LinearLayout.LayoutParams(context.dp(42), context.dp(42)).apply {
            marginEnd = context.dp(10)
        }
    )

    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val headline = TextView(context).apply {
        text = ad.headline
        textSize = 15f
        setTextColor(onSurface)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    textColumn.addView(headline)

    val supportingText = ad.advertiser?.takeIf { it.isNotBlank() }
        ?: ad.body?.takeIf { it.isNotBlank() }
    val supporting = TextView(context).apply {
        text = supportingText.orEmpty()
        textSize = 12f
        setTextColor(onSurfaceVariant)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = if (supportingText == null) View.GONE else View.VISIBLE
    }
    textColumn.addView(supporting)
    content.addView(
        textColumn,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    )

    val action = Button(context).apply {
        text = ad.callToAction.orEmpty()
        textSize = 12f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = context.dp(40)
        minimumHeight = context.dp(40)
        setPadding(context.dp(10), 0, context.dp(10), 0)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    content.addView(
        action,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = context.dp(8)
        }
    )
    root.addView(content)

    nativeView.addView(root)
    nativeView.adChoicesView = adChoices
    nativeView.headlineView = headline
    nativeView.iconView = icon
    nativeView.callToActionView = action
    if (!ad.advertiser.isNullOrBlank()) {
        nativeView.advertiserView = supporting
    } else {
        nativeView.bodyView = supporting
    }
    nativeView.setNativeAd(ad)
    return nativeView
}

private fun android.content.Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

private fun buildNativeAdView(
    context: android.content.Context,
    ad: NativeAd,
    onSurface: Int,
    onSurfaceVariant: Int
): NativeAdView {
    val nativeView = NativeAdView(context)
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(12, 8, 12, 8)
        setBackgroundColor(Color.TRANSPARENT)
    }
    val attribution = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    attribution.addView(TextView(context).apply {
        text = "Ad"
        textSize = 12f
        setTextColor(onSurfaceVariant)
    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    val adChoices = AdChoicesView(context)
    attribution.addView(adChoices)
    root.addView(attribution)

    val mediaView = MediaView(context)
    root.addView(mediaView, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        (120 * context.resources.displayMetrics.density).toInt()
    ))
    val headline = TextView(context).apply {
        text = ad.headline
        textSize = 16f
        setTextColor(onSurface)
        setPadding(0, 8, 0, 2)
    }
    val body = TextView(context).apply {
        text = ad.body.orEmpty()
        textSize = 13f
        setTextColor(onSurfaceVariant)
        visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    val icon = ImageView(context).apply {
        ad.icon?.drawable?.let(::setImageDrawable)
        visibility = if (ad.icon == null) View.GONE else View.VISIBLE
    }
    val action = Button(context).apply {
        text = ad.callToAction.orEmpty()
        isAllCaps = false
        visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    root.addView(headline)
    root.addView(body)
    root.addView(icon, LinearLayout.LayoutParams(48, 48))
    root.addView(action)
    nativeView.addView(root)
    nativeView.mediaView = mediaView
    nativeView.adChoicesView = adChoices
    nativeView.headlineView = headline
    nativeView.bodyView = body
    nativeView.iconView = icon
    nativeView.callToActionView = action
    nativeView.setNativeAd(ad)
    return nativeView
}
