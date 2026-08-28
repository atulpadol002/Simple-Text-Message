package com.ap.simpletextmessage.premium

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ap.simpletextmessage.ads.AdRemoteConfigManager
import androidx.annotation.StringRes
import com.ap.simpletextmessage.R

object LegalLinks {
    fun openPrivacyPolicy(context: Context) {
        openExternalUrl(
            context,
            resolvedOrFallback(
                AdRemoteConfigManager.privacyPolicyUrl.value,
                AdRemoteConfigManager.PRIVACY_POLICY_FALLBACK_URL
            ),
            R.string.privacy_policy
        )
    }

    fun openTermsAndConditions(context: Context) {
        openExternalUrl(
            context,
            resolvedOrFallback(
                AdRemoteConfigManager.termsConditionsUrl.value,
                AdRemoteConfigManager.TERMS_CONDITIONS_FALLBACK_URL
            ),
            R.string.terms_conditions
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
            openUri(context, uri, R.string.subscription_management)
        } catch (_: SecurityException) {
            openUri(context, uri, R.string.subscription_management)
        }
    }

    private fun openExternalUrl(context: Context, url: String?, @StringRes labelRes: Int) {
        val label = context.getString(labelRes)
        if (url.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.url_not_configured, label), Toast.LENGTH_LONG).show()
            return
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("https", "http") || uri.host.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.url_invalid, label), Toast.LENGTH_LONG).show()
            return
        }
        openUri(context, uri, labelRes)
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

    private fun openUri(context: Context, uri: Uri, @StringRes labelRes: Int) {
        val label = context.getString(labelRes)
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .asNewTaskWhenNeeded(context)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.no_app_can_open, label), Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, context.getString(R.string.unable_open_label, label), Toast.LENGTH_LONG).show()
        }
    }

    private fun Intent.asNewTaskWhenNeeded(context: Context): Intent = apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
