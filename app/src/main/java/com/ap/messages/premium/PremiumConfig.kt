package com.ap.messages.premium

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
    val displayName: String,
    val basePlanId: String,
    val billingPeriod: String
) {
    MONTHLY("Monthly", PremiumConfig.MONTHLY_BASE_PLAN_ID, "P1M"),
    YEARLY("Yearly", PremiumConfig.YEARLY_BASE_PLAN_ID, "P1Y")
}
