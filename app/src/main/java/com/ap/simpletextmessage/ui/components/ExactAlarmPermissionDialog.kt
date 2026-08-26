package com.ap.simpletextmessage.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ExactAlarmPermissionDialog(
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Allow scheduled messages") },
        text = {
            Text(
                "To send scheduled messages at the time you choose, allow Simple Text Message to set alarms and reminders."
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

fun exactAlarmSettingsIntent(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null

    val packageUri = Uri.parse("package:${context.packageName}")
    val candidates = listOf(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        Intent(Settings.ACTION_SETTINGS)
    )
    return candidates.firstOrNull { intent ->
        intent.resolveActivity(context.packageManager) != null
    }
}
