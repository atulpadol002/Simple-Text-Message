package com.ap.messages.ads

import android.graphics.Color
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

@Composable
fun NativeAdCard(
    placement: AdPlacement,
    enabled: Boolean,
    maxPerSession: Int,
    modifier: Modifier = Modifier,
    onShown: () -> Unit = {}
) {
    val config by AdRemoteConfigManager.config.collectAsState()
    val adsReady by AdRuntime.mobileAdsReady.collectAsState()
    val consent by AdConsentManager.canRequestAds.collectAsState()
    val adsAllowed by AdRuntime.adsAllowed.collectAsState()
    val session by AdSessionManager.snapshot.collectAsState()
    if (!enabled || maxPerSession <= 0 || session.count(placement) >= maxPerSession ||
        !adsAllowed || !config.masterEnabled || !adsReady || !consent ||
        !AdSessionManager.canShowNonRewarded(config)
    ) return

    val context = LocalContext.current
    val currentOnShown by rememberUpdatedState(onShown)
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    val impressionRecorded = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val disposed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    LaunchedEffect(placement) {
        AdLoader.Builder(context, AdUnitIds.native)
            .forNativeAd { loaded ->
                if (disposed.get()) {
                    loaded.destroy()
                } else {
                    nativeAd?.destroy()
                    nativeAd = loaded
                    currentOnShown()
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdImpression() {
                    if (impressionRecorded.compareAndSet(false, true) &&
                        AdSessionManager.canShowNonRewarded(AdRemoteConfigManager.config.value)
                    ) {
                        AdSessionManager.recordNonRewardedShown(placement)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAd = null
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }
    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd ?: return
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            factory = { buildNativeAdView(it, ad, onSurface, onSurfaceVariant) },
            update = { view -> view.setNativeAd(ad) },
            onRelease = { it.destroy() }
        )
    }
}

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
