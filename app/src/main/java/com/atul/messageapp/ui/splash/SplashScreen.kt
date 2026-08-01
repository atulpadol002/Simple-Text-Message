package com.atul.messageapp.ui.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.atul.messageapp.sms.DefaultSmsManager

@Composable
fun SplashScreen(
    onPermissionFlow: () -> Unit,
    onDirectHome: () -> Unit
) {
    val context = LocalContext.current

    val isDefaultSmsApp = remember(context) {
        DefaultSmsManager(context).isDefaultSmsApp()
    }

    LaunchedEffect(isDefaultSmsApp) {
        if (isDefaultSmsApp) {
            onDirectHome()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Message App"
        )

        if (!isDefaultSmsApp) {
            Button(
                onClick = {
                    onPermissionFlow()
                }
            ) {
                Text(
                    text = "Get Started"
                )
            }
        }
    }
}
