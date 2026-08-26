package com.ap.simpletextmessage.ui.premium

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ap.simpletextmessage.R
import com.ap.simpletextmessage.premium.LegalLinks
import com.ap.simpletextmessage.premium.PremiumBillingManager
import com.ap.simpletextmessage.premium.PremiumEntitlementStatus
import com.ap.simpletextmessage.premium.PremiumPlan
import com.ap.simpletextmessage.premium.PremiumPlanUi

internal val PremiumGreen = Color(0xFF079B2B)
internal val PremiumGreenDark = Color(0xFF056F20)
internal val PremiumGreenSoft = Color(0xFFE8F7EC)
internal val PremiumInk = Color(0xFF17201A)
internal val PremiumMuted = Color(0xFF5D6A60)

@Composable
fun PaywallScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val billingState by PremiumBillingManager.state.collectAsState()
    var selectedPlanName by rememberSaveable { mutableStateOf(PremiumPlan.YEARLY.name) }
    val selectedPlan = PremiumPlan.valueOf(selectedPlanName)
    val selectedPlanUi = when (selectedPlan) {
        PremiumPlan.MONTHLY -> billingState.monthly
        PremiumPlan.YEARLY -> billingState.yearly
    }

    BackHandler(onBack = onBackClick)

    LaunchedEffect(Unit) {
        PremiumBillingManager.refreshProductDetails()
    }

    LaunchedEffect(billingState.monthly.available, billingState.yearly.available) {
        if (!selectedPlanUi.available) {
            selectedPlanName = when {
                billingState.yearly.available -> PremiumPlan.YEARLY.name
                billingState.monthly.available -> PremiumPlan.MONTHLY.name
                else -> selectedPlanName
            }
        }
    }

    LaunchedEffect(billingState.message) {
        billingState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            PremiumBillingManager.clearMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-70).dp, y = (-85).dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(PremiumGreenSoft)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = 95.dp)
                .size(150.dp)
                .clip(CircleShape)
                .background(PremiumGreen.copy(alpha = 0.07f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Go Premium",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.paywall_icon),
                    contentDescription = "Premium",
                    modifier = Modifier.size(width = 176.dp, height = 118.dp),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = "Go Premium",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Enjoy a clean, ad-free messaging experience.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))

                NoAdsBenefitCard()
                Spacer(Modifier.height(20.dp))

                if (billingState.isPremium) {
                    ActiveSubscriptionCard()
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { LegalLinks.openSubscriptionManagement(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumGreen)
                    ) {
                        Text("Manage Subscription", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text(
                        text = "Choose your plan",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(10.dp))

                    SubscriptionPlanCard(
                        plan = billingState.monthly,
                        selected = selectedPlan == PremiumPlan.MONTHLY,
                        recommended = false,
                        supportingText = "Flexible monthly plan",
                        onSelect = { selectedPlanName = PremiumPlan.MONTHLY.name }
                    )
                    Spacer(Modifier.height(12.dp))
                    SubscriptionPlanCard(
                        plan = billingState.yearly,
                        selected = selectedPlan == PremiumPlan.YEARLY,
                        recommended = true,
                        supportingText = "One annual payment",
                        onSelect = { selectedPlanName = PremiumPlan.YEARLY.name }
                    )
                    Spacer(Modifier.height(18.dp))

                    val purchaseEnabled = selectedPlanUi.available &&
                        !billingState.purchaseInProgress &&
                        billingState.entitlementStatus != PremiumEntitlementStatus.CHECKING
                    Button(
                        onClick = {
                            (context as? Activity)?.let { activity ->
                                PremiumBillingManager.launchPurchase(activity, selectedPlan)
                            }
                        },
                        enabled = purchaseEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumGreen)
                    ) {
                        if (billingState.purchaseInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Continue with ${selectedPlan.displayName}",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = PremiumBillingManager::restorePurchases,
                    enabled = billingState.entitlementStatus != PremiumEntitlementStatus.CHECKING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, PremiumGreen)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = PremiumGreen
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Restore Purchase", color = PremiumGreen, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { LegalLinks.openTermsAndConditions(context) }) {
                        Text("Terms", color = PremiumMuted)
                    }
                    Text("•", color = PremiumMuted)
                    TextButton(onClick = { LegalLinks.openPrivacyPolicy(context) }) {
                        Text("Privacy Policy", color = PremiumMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoAdsBenefitCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumGreenSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = PremiumGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = "Remove All Ads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PremiumInk
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "No banners, native, interstitial, rewarded or app-open ads for an active No Ads subscriber where applicable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PremiumMuted
                )
            }
        }
    }
}

@Composable
private fun ActiveSubscriptionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumGreenSoft)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = PremiumGreen,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "No Ads is active",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PremiumGreenDark
                )
                Text(
                    text = "Your active subscription keeps messaging ad-free.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PremiumMuted
                )
            }
        }
    }
}

@Composable
private fun SubscriptionPlanCard(
    plan: PremiumPlanUi,
    selected: Boolean,
    recommended: Boolean,
    supportingText: String,
    onSelect: () -> Unit
) {
    val borderColor = if (selected) PremiumGreen else Color(0xFFDCE3DD)
    val background = if (selected) PremiumGreenSoft else Color.White
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = plan.available, onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = plan.available,
                colors = RadioButtonDefaults.colors(selectedColor = PremiumGreen)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan.plan.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PremiumInk
                    )
                    if (recommended) {
                        Spacer(Modifier.size(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PremiumGreen
                        ) {
                            Text(
                                text = "Best Value",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (plan.available) supportingText else "Unavailable from Google Play",
                    style = MaterialTheme.typography.bodySmall,
                    color = PremiumMuted
                )
            }
            Text(
                text = plan.formattedPrice ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (plan.available) PremiumGreenDark else PremiumMuted,
                textAlign = TextAlign.End
            )
        }
    }
}
