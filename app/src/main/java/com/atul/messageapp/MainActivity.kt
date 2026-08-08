package com.atul.messageapp

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.atul.messageapp.navigation.AppNavigation
import com.atul.messageapp.theme.MessageAppTheme
import com.atul.messageapp.viewmodel.ThemeViewModel
import com.atul.messageapp.notifications.MessageNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingChatDestination(
    val threadId: Long,
    val address: String,
    val contactName: String
)

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private val _pendingChatDestination = MutableStateFlow<PendingChatDestination?>(null)
    val pendingChatDestination: StateFlow<PendingChatDestination?> =
        _pendingChatDestination.asStateFlow()

    var showNotificationSettingsPrompt by mutableStateOf(false)
        private set

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        MessageNotificationManager.createChannel(this)
        captureNavigationIntent(intent)

        setContent {

            val themeViewModel: ThemeViewModel =
                viewModel()

            val themeMode by
            themeViewModel
                .themeMode
                .collectAsState()

            MessageAppTheme(
                themeMode = themeMode
            ) {
                AppNavigation(
                    themeMode = themeMode,
                    onThemeSelected = { selectedTheme ->
                        themeViewModel.changeTheme(
                            selectedTheme
                        )
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNavigationIntent(intent)
    }

    fun consumePendingChat(destination: PendingChatDestination) {
        if (_pendingChatDestination.value == destination) {
            _pendingChatDestination.value = null
        }
    }

    fun requestNotificationPermissionAtAppEntry() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED ||
            notificationPermissionAttemptedForProcess
        ) return

        notificationPermissionAttemptedForProcess = true
        val preferences = getSharedPreferences(NOTIFICATION_PERMISSION_PREFS, MODE_PRIVATE)
        val requestedBefore = preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
        val systemCanExplainDenial = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        )

        if (!requestedBefore || systemCanExplainDenial) {
            preferences.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showNotificationSettingsPrompt = true
        }
    }

    fun dismissNotificationSettingsPrompt() {
        showNotificationSettingsPrompt = false
    }

    fun openNotificationSettings() {
        showNotificationSettingsPrompt = false
        MessageNotificationManager.openAppNotificationSettings(this)
    }

    private fun captureNavigationIntent(source: Intent) {
        val notificationThreadId = source.getLongExtra(
            MessageNotificationManager.EXTRA_THREAD_ID,
            0L
        )
        val isNotificationTap =
            source.action == MessageNotificationManager.ACTION_OPEN_CHAT || notificationThreadId > 0L
        val sendToAddress = if (source.action == Intent.ACTION_SENDTO) {
            source.data?.schemeSpecificPart?.substringBefore('?').orEmpty()
        } else {
            ""
        }
        val address = if (isNotificationTap) {
            source.getStringExtra(MessageNotificationManager.EXTRA_ADDRESS).orEmpty()
        } else {
            sendToAddress
        }

        if ((notificationThreadId > 0L || sendToAddress.isNotBlank()) && address.isNotBlank()) {
            _pendingChatDestination.value = PendingChatDestination(
                threadId = notificationThreadId,
                address = address,
                contactName = source.getStringExtra(
                    MessageNotificationManager.EXTRA_CONTACT_NAME
                ).orEmpty().ifBlank { address }
            )
        }

        source.removeExtra(MessageNotificationManager.EXTRA_THREAD_ID)
        source.removeExtra(MessageNotificationManager.EXTRA_ADDRESS)
        source.removeExtra(MessageNotificationManager.EXTRA_CONTACT_NAME)
        if (source.action == MessageNotificationManager.ACTION_OPEN_CHAT || source.action == Intent.ACTION_SENDTO) {
            source.data = null
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_PREFS = "notification_permission_state"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "requested_before"
        @Volatile
        private var notificationPermissionAttemptedForProcess = false
    }
}
