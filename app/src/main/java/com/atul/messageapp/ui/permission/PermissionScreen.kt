package com.atul.messageapp.ui.permission

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import com.atul.messageapp.sms.DefaultSmsManager

@Composable
fun PermissionScreen(
    onPermissionGranted: () -> Unit
) {

    val context = LocalContext.current

    val defaultSmsManager =
        DefaultSmsManager(context)

    val contactsPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {
            onPermissionGranted()
        }

    fun continueAfterDefaultRoleGranted() {

        val contactsPermissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

        if (contactsPermissionGranted) {
            onPermissionGranted()
        } else {
            contactsPermissionLauncher.launch(
                Manifest.permission.READ_CONTACTS
            )
        }
    }

    val roleLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {

            if (defaultSmsManager.isDefaultSmsApp()) {
                continueAfterDefaultRoleGranted()
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

                    continueAfterDefaultRoleGranted()

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
