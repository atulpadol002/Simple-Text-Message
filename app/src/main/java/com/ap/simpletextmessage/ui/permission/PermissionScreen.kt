package com.ap.simpletextmessage.ui.permission

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ap.simpletextmessage.R
import com.ap.simpletextmessage.sms.DefaultSmsManager
import com.ap.simpletextmessage.ads.AdRuntime
import com.ap.simpletextmessage.ads.AdPlacement
import com.ap.simpletextmessage.ads.AdRemoteConfigManager
import com.ap.simpletextmessage.ads.AdType
import com.ap.simpletextmessage.ads.AdTypePlacement
import com.ap.simpletextmessage.ads.NativeAdCard

@Composable
fun PermissionScreen(
    isDefaultSmsApp: Boolean,
    missingSmsPermissions: List<String>,
    onPermissionStateChanged: () -> Unit,
    onRequestSmsPermissions: () -> Unit
) {

    val context = LocalContext.current
    val adConfig by AdRemoteConfigManager.config.collectAsState()
    val adTypeConfig by AdRemoteConfigManager.adTypeConfig.collectAsState()
    val defaultSmsNativeEnabled = !isDefaultSmsApp && adConfig.defaultSmsNative.enabled &&
        adTypeConfig.allows(AdTypePlacement.DEFAULT_SMS, AdType.NATIVE)
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
            painter = painterResource(R.drawable.simple_text_message_app_icon),
            contentDescription = stringResource(R.string.app_logo_description),
            modifier = Modifier.size(104.dp),
            contentScale = ContentScale.Fit
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isDefaultSmsApp) {
                    stringResource(R.string.allow_sms_access)
                } else {
                    stringResource(R.string.make_default_sms_app)
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (isDefaultSmsApp) {
                    stringResource(R.string.sms_permissions_explanation)
                } else {
                    stringResource(R.string.default_sms_explanation)
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
                    stringResource(R.string.send_receive_sms),
                    stringResource(R.string.smart_search),
                    stringResource(R.string.schedule_messages),
                    stringResource(R.string.archive_star_block)
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
                    AdRuntime.suppressNextAppOpen()
                    defaultSmsManager
                        .createRequestRoleIntent()
                        ?.let(roleLauncher::launch)
                        ?: onPermissionStateChanged()
                }
            }
        ) {
            Text(
                if (isDefaultSmsApp) {
                    stringResource(R.string.allow_sms_permissions)
                } else {
                    stringResource(R.string.set_default_sms_app)
                }
            )
        }

        NativeAdCard(
            placement = AdPlacement.DEFAULT_SMS_NATIVE,
            enabled = defaultSmsNativeEnabled,
            maxPerSession = adConfig.defaultSmsNative.maxPerSession,
            modifier = Modifier.widthIn(max = 420.dp),
            compact = true,
            cacheKey = "default_sms_native"
        )
    }
}
