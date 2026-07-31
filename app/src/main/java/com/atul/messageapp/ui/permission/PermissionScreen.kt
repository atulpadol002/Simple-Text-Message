package com.atul.messageapp.ui.permission

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
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

@Composable
fun PermissionScreen(
    onPermissionGranted: () -> Unit
) {

    val context = LocalContext.current

    val roleLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {

            if (isDefaultSmsApp(context)) {
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

                if (isDefaultSmsApp(context)) {

                    onPermissionGranted()

                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                    val roleManager =
                        context.getSystemService(RoleManager::class.java)

                    if (
                        roleManager.isRoleAvailable(RoleManager.ROLE_SMS)
                    ) {

                        val intent =
                            roleManager.createRequestRoleIntent(
                                RoleManager.ROLE_SMS
                            )

                        roleLauncher.launch(intent)
                    }
                }
            }
        ) {
            Text("Set as Default SMS App")
        }
    }
}

private fun isDefaultSmsApp(
    context: Context
): Boolean {

    return if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    ) {

        val roleManager =
            context.getSystemService(RoleManager::class.java)

        roleManager.isRoleHeld(RoleManager.ROLE_SMS)

    } else {

        android.provider.Telephony.Sms
            .getDefaultSmsPackage(context) ==
                context.packageName
    }
}