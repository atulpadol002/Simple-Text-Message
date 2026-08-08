package com.atul.messageapp.ui.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atul.messageapp.R
import com.atul.messageapp.sms.DefaultSmsManager

@Composable
fun PermissionScreen(
    isDefaultSmsApp: Boolean,
    missingSmsPermissions: List<String>,
    onPermissionStateChanged: () -> Unit,
    onRequestSmsPermissions: () -> Unit
) {

    val context = LocalContext.current
    val defaultSmsManager =
        DefaultSmsManager(context)

    val roleLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            onPermissionStateChanged()
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))

        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.message_logo_symbol),
            contentDescription = "Message App logo",
            modifier = Modifier.size(112.dp),
            contentScale = ContentScale.Fit
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isDefaultSmsApp) {
                    "Allow Message App to access SMS"
                } else {
                    "Make Message App your default SMS app"
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (isDefaultSmsApp) {
                    "The required SMS permissions are needed to display, send and receive your messages."
                } else {
                    "Continue sending, receiving, searching, scheduling and managing all your SMS securely in one place."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    "Send & Receive SMS",
                    "Smart Search",
                    "Schedule Messages",
                    "Archive • Star • Block"
                ).forEach { feature ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = feature,
                            modifier = Modifier.padding(start = 14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            onClick = {
                if (isDefaultSmsApp) {
                    if (missingSmsPermissions.isNotEmpty()) {
                        onRequestSmsPermissions()
                    } else {
                        onPermissionStateChanged()
                    }
                } else {
                    defaultSmsManager
                        .createRequestRoleIntent()
                        ?.let(roleLauncher::launch)
                        ?: onPermissionStateChanged()
                }
            }
        ) {
            Text(
                if (isDefaultSmsApp) {
                    "Allow SMS permissions"
                } else {
                    "Set as Default SMS App"
                }
            )
        }
    }
}
