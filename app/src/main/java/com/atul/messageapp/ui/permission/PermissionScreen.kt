package com.atul.messageapp.ui.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.atul.messageapp.sms.DefaultSmsManager

@Composable
fun PermissionScreen(
    onPermissionGranted: () -> Unit
) {

    val context = LocalContext.current

    val defaultSmsManager =
        DefaultSmsManager(context)

    val roleLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {

            if (defaultSmsManager.isDefaultSmsApp()) {
                onPermissionGranted()
            }
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Set this app as your default SMS app to continue")

        Button(
            onClick = {

                if (defaultSmsManager.isDefaultSmsApp()) {

                    onPermissionGranted()

                } else {

                    defaultSmsManager
                        .createRequestRoleIntent()
                        ?.let(roleLauncher::launch)
                }
            }
        ) {
            Text("Set as Default SMS App")
        }
    }
}
