package com.ap.simpletextmessage.premium

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ap.simpletextmessage.ads.AdRemoteConfigManager

object LegalLinks {
    fun openPrivacyPolicy(context: Context) {
        openExternalUrl(
            context,
            resolvedOrFallback(
                AdRemoteConfigManager.privacyPolicyUrl.value,
                AdRemoteConfigManager.PRIVACY_POLICY_FALLBACK_URL
            ),
            "Privacy Policy"
        )
    }

    fun openTermsAndConditions(context: Context) {
        openExternalUrl(
            context,
            resolvedOrFallback(
                AdRemoteConfigManager.termsConditionsUrl.value,
                AdRemoteConfigManager.TERMS_CONDITIONS_FALLBACK_URL
            ),
            "Terms & Conditions"
        )
    }

    fun openSubscriptionManagement(context: Context) {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${Uri.encode(PremiumConfig.SUBSCRIPTION_PRODUCT_ID)}" +
                "&package=${Uri.encode(context.packageName)}"
        )
        val playStoreIntent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage("com.android.vending")
            .asNewTaskWhenNeeded(context)
        try {
            context.startActivity(playStoreIntent)
        } catch (_: ActivityNotFoundException) {
            openUri(context, uri, "Google Play subscription management")
        } catch (_: SecurityException) {
            openUri(context, uri, "Google Play subscription management")
        }
    }

    private fun openExternalUrl(context: Context, url: String?, label: String) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, "$label URL is not configured", Toast.LENGTH_LONG).show()
            return
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("https", "http") || uri.host.isNullOrBlank()) {
            Toast.makeText(context, "$label URL is invalid", Toast.LENGTH_LONG).show()
            return
        }
        openUri(context, uri, label)
    }

    private fun resolvedOrFallback(value: String?, fallback: String): String {
        val candidate = value?.trim().orEmpty()
        val uri = runCatching { Uri.parse(candidate) }.getOrNull()
        val host = uri?.host
        return if (uri?.scheme?.lowercase() in setOf("http", "https") && !host.isNullOrBlank()) {
            candidate
        } else {
            fallback
        }
    }

    private fun openUri(context: Context, uri: Uri, label: String) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .asNewTaskWhenNeeded(context)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open $label", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "Unable to open $label", Toast.LENGTH_LONG).show()
        }
    }

    private fun Intent.asNewTaskWhenNeeded(context: Context): Intent = apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
