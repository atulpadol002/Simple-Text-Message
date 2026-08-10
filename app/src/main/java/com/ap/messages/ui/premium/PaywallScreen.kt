package com.ap.messages.ui.premium

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ap.messages.premium.LegalLinks
import com.ap.messages.premium.PremiumBillingManager
import com.ap.messages.premium.PremiumEntitlementStatus
import com.ap.messages.premium.PremiumPlan
import com.ap.messages.premium.PremiumPlanUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val billingState by PremiumBillingManager.state.collectAsState()
    var selectedPlan by rememberSaveable { mutableStateOf(PremiumPlan.YEARLY) }

    LaunchedEffect(Unit) {
        PremiumBillingManager.refreshProductDetails()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Premium") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    modifier = Modifier.padding(22.dp).size(72.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Go Ad-Free",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Enjoy Messages without ads.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "NO ADS",
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PlanChoice(
                plan = billingState.monthly,
                selected = selectedPlan == PremiumPlan.MONTHLY,
                recommended = false,
                onSelect = { selectedPlan = PremiumPlan.MONTHLY }
            )
            Spacer(Modifier.height(12.dp))
            PlanChoice(
                plan = billingState.yearly,
                selected = selectedPlan == PremiumPlan.YEARLY,
                recommended = true,
                onSelect = { selectedPlan = PremiumPlan.YEARLY }
            )
            Spacer(Modifier.height(22.dp))
            val selectedAvailable = when (selectedPlan) {
                PremiumPlan.MONTHLY -> billingState.monthly.available
                PremiumPlan.YEARLY -> billingState.yearly.available
            }
            Button(
                onClick = {
                    (context as? Activity)?.let { activity ->
                        PremiumBillingManager.launchPurchase(activity, selectedPlan)
                    }
                },
                enabled = selectedAvailable && !billingState.purchaseInProgress &&
                    !billingState.isPremium,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (billingState.purchaseInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Subscribe")
                }
            }
            TextButton(
                onClick = PremiumBillingManager::restorePurchases,
                enabled = billingState.entitlementStatus != PremiumEntitlementStatus.CHECKING
            ) {
                Text("Restore Purchases")
            }
            if (billingState.isPremium) {
                Text(
                    text = "No Ads is active.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            billingState.message?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { LegalLinks.openPrivacyPolicy(context) }) {
                    Text("Privacy Policy")
                }
                Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { LegalLinks.openTermsAndConditions(context) }) {
                    Text("Terms & Conditions")
                }
            }
        }
    }
}

@Composable
private fun PlanChoice(
    plan: PremiumPlanUi,
    selected: Boolean,
    recommended: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan.plan.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (recommended) {
                        Surface(
                            modifier = Modifier.padding(start = 10.dp),
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "Recommended",
                                    modifier = Modifier.padding(start = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
                Text(
                    text = plan.formattedPrice?.let { price ->
                        "$price / ${if (plan.plan == PremiumPlan.MONTHLY) "month" else "year"}"
                    } ?: "Unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
