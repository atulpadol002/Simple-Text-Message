package com.ap.simpletextmessage.premium

import androidx.annotation.StringRes
import com.ap.simpletextmessage.R

/**
 * TODO before release: create this subscription and both auto-renewing base plans in Play Console,
 * or replace these IDs here with the IDs that already exist in Play Console.
 */
object PremiumConfig {
    const val SUBSCRIPTION_PRODUCT_ID = "no_ads"
    const val MONTHLY_BASE_PLAN_ID = "monthly"
    const val YEARLY_BASE_PLAN_ID = "yearly"
}

enum class PremiumPlan(
    @StringRes val displayNameRes: Int,
    val basePlanId: String,
    val billingPeriod: String
) {
    MONTHLY(R.string.monthly, PremiumConfig.MONTHLY_BASE_PLAN_ID, "P1M"),
    YEARLY(R.string.yearly, PremiumConfig.YEARLY_BASE_PLAN_ID, "P1Y")
}
