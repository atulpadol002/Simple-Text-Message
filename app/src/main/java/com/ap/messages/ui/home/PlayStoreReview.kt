package com.ap.messages.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

private const val AppPackageName = "com.ap.messages"

/** Process-local guard: the drawer prompt is offered at most once per app session. */
internal object RateUsSession {
    var wasDialogShown: Boolean = false
        private set

    fun markDialogShown() {
        wasDialogShown = true
    }
}

internal fun launchPlayStoreReview(context: Context) {
    val activity = context.findActivity()
    if (activity == null) {
        openPlayStoreListing(context)
        return
    }

    val reviewManager = ReviewManagerFactory.create(activity)
    reviewManager.requestReviewFlow()
        .addOnSuccessListener { reviewInfo ->
            // Completion only means the flow ended; Play does not reveal whether a review was submitted.
            reviewManager.launchReviewFlow(activity, reviewInfo)
                .addOnFailureListener { openPlayStoreListing(activity) }
        }
        .addOnFailureListener {
            openPlayStoreListing(activity)
        }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openPlayStoreListing(context: Context) {
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$AppPackageName")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        setPackage("com.android.vending")
    }

    if (marketIntent.resolveActivity(context.packageManager) != null) {
        if (runCatching { context.startActivity(marketIntent) }.isSuccess) return
    }

    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$AppPackageName")
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    if (webIntent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(webIntent) }
    }
}
