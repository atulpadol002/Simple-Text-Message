package com.ap.messages

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
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
import com.ap.messages.navigation.AppNavigation
import com.ap.messages.ads.BannerAdHost
import com.ap.messages.theme.MessageAppTheme
import com.ap.messages.viewmodel.ThemeViewModel
import com.ap.messages.notifications.MessageNotificationManager
import com.ap.messages.sms.DefaultSmsManager
import com.ap.messages.ads.AdRuntime
import com.ap.messages.ads.AppOpenAdManager
import com.ap.messages.ads.AppOpenReason
import com.ap.messages.ads.AdRemoteConfigManager
import com.ap.messages.ads.InterstitialAdManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ap.messages.ads.AdDebug
import com.ap.messages.ads.AutoInterstitialManager
import com.ap.messages.premium.PremiumBillingManager

data class PendingChatDestination(
    val threadId: Long,
    val address: String,
    val contactName: String,
    val navigationToken: String = ""
)

data class AppPermissionState(
    val revision: Long = 0L,
    val isDefaultSmsApp: Boolean = false,
    val missingSmsPermissions: List<String> = emptyList(),
    val contactsGranted: Boolean = false,
    val notificationsGranted: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
) {
    val hasCoreMessagingAccess: Boolean
        get() = isDefaultSmsApp && missingSmsPermissions.isEmpty()
}

class MainActivity : ComponentActivity() {

    private data class PendingAdResume(
        val returnedFromBackground: Boolean,
        val backgroundDurationMillis: Long
    )

    private var wasBackgrounded = false
    private var backgroundedAtElapsedRealtime = 0L
    private var pendingAdResume: PendingAdResume? = null
    private val pendingAdResumeObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            val pending = pendingAdResume ?: return@LifecycleEventObserver
            pendingAdResume = null
            continueAdResume(
                pending.returnedFromBackground,
                pending.backgroundDurationMillis
            )
        }
    }

    private val smsPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        activePermissionRequest = PermissionRequest.NONE
        val state = refreshPermissionState(forceEmit = true)
        if (
            state.missingSmsPermissions.isNotEmpty() &&
            state.missingSmsPermissions.any { permission ->
                !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            }
        ) {
            showSmsSettingsPrompt = true
        }
    }

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        activePermissionRequest = PermissionRequest.NONE
        val state = refreshPermissionState(forceEmit = true)
        if (state.contactsGranted) {
            contactsStepResolvedForProcess = true
        } else if (
            !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_CONTACTS
            )
        ) {
            showContactsSettingsPrompt = true
        } else {
            contactsStepResolvedForProcess = true
            refreshPermissionState(forceEmit = true)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        activePermissionRequest = PermissionRequest.NONE
        val state = refreshPermissionState(forceEmit = true)
        if (state.notificationsGranted) {
            notificationStepResolvedForProcess = true
        } else if (
            !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            showNotificationSettingsPrompt = true
        } else {
            notificationStepResolvedForProcess = true
        }
    }

    private val _pendingChatDestination = MutableStateFlow<PendingChatDestination?>(null)
    val pendingChatDestination: StateFlow<PendingChatDestination?> =
        _pendingChatDestination.asStateFlow()

    private val _permissionState = MutableStateFlow(AppPermissionState())
    val permissionState: StateFlow<AppPermissionState> = _permissionState.asStateFlow()

    var showSmsSettingsPrompt by mutableStateOf(false)
        private set

    var showContactsSettingsPrompt by mutableStateOf(false)
        private set

    var showNotificationSettingsPrompt by mutableStateOf(false)
        private set

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(pendingAdResumeObserver)
        PremiumBillingManager.initialize(applicationContext)
        AdRuntime.initialize(this)
        MessageNotificationManager.createChannel(this)
        captureNavigationIntent(intent)
        refreshPermissionState(forceEmit = true)

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
                BannerAdHost {
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNavigationIntent(intent)
        AdRuntime.suppressNextAppOpen()
    }

    fun consumePendingChat(destination: PendingChatDestination) {
        if (_pendingChatDestination.value == destination) {
            _pendingChatDestination.value = null
            if (destination.navigationToken.isNotBlank()) {
                synchronized(handledNavigationTokens) {
                    handledNavigationTokens += destination.navigationToken
                    while (handledNavigationTokens.size > MAX_HANDLED_NAVIGATION_TOKENS) {
                        handledNavigationTokens.remove(handledNavigationTokens.first())
                    }
                }
            }
        }
    }

    fun isAdPresentationSafe(): Boolean =
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            permissionState.value.hasCoreMessagingAccess &&
            onboardingPermissionStepsComplete(permissionState.value) &&
            !showSmsSettingsPrompt && !showContactsSettingsPrompt && !showNotificationSettingsPrompt &&
            pendingChatDestination.value == null

    override fun onResume() {
        super.onResume()
        AutoInterstitialManager.onForeground()
        refreshPermissionState()
        val returnedFromBackground = wasBackgrounded
        val backgroundDurationMillis = if (returnedFromBackground) {
            (SystemClock.elapsedRealtime() - backgroundedAtElapsedRealtime).coerceAtLeast(0L)
        } else 0L
        wasBackgrounded = false

        PremiumBillingManager.refreshPurchases {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    continueAdResume(returnedFromBackground, backgroundDurationMillis)
                } else {
                    pendingAdResume = PendingAdResume(
                        returnedFromBackground,
                        backgroundDurationMillis
                    )
                }
            }
        }
    }

    private fun continueAdResume(
        returnedFromBackground: Boolean,
        backgroundDurationMillis: Long
    ) {
        val state = refreshPermissionState()
        val adSafe = state.hasCoreMessagingAccess &&
            onboardingPermissionStepsComplete(state) &&
            !showSmsSettingsPrompt && !showContactsSettingsPrompt && !showNotificationSettingsPrompt &&
            _pendingChatDestination.value == null
        AdDebug.log {
            "Consent launch gate: adSafe=$adSafe defaultSms=${state.isDefaultSmsApp} " +
                "coreSmsAccess=${state.hasCoreMessagingAccess} onboardingComplete=" +
                onboardingPermissionStepsComplete(state)
        }
        if (adSafe) AdRuntime.gatherConsent(this)
        else AdDebug.log { "Consent not gathered; MobileAds initialization and ad requests remain blocked" }

        if (adSafe && !onboardingAdOpportunityConsumed) {
            onboardingAdOpportunityConsumed = true
            // Clear permission/default-role return suppression at the one-shot onboarding gate.
            AdRuntime.consumeAppOpenSuppression()
            val onboardingShown = InterstitialAdManager.onOnboardingCompleted(
                this,
                activitySafe = true
            )
            if (!onboardingShown) {
                AppOpenAdManager.maybeShow(
                    activity = this,
                    activitySafe = true,
                    reason = AppOpenReason.AFTER_ONBOARDING
                )
            }
        } else if (returnedFromBackground) {
            val suppressed = AdRuntime.consumeAppOpenSuppression()
            val longEnough = backgroundDurationMillis >= MIN_WARM_RESUME_BACKGROUND_MILLIS
            if (!suppressed && longEnough) {
                AppOpenAdManager.maybeShow(
                    activity = this,
                    activitySafe = adSafe,
                    reason = AppOpenReason.WARM_RESUME
                )
            } else {
                AdDebug.log {
                    "App Open reason=warm_resume eligible=false " +
                        "ready=${AppOpenAdManager.isReady()} shown=false " +
                        "blockedReason=${if (suppressed) "suppressed_return" else "short_interruption"}"
                }
            }
        }
    }

    override fun onStop() {
        AutoInterstitialManager.onBackground()
        super.onStop()
        if (!isChangingConfigurations) {
            wasBackgrounded = true
            backgroundedAtElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }

    fun refreshPermissionsAfterRoleRequest() {
        refreshPermissionState(forceEmit = true)
    }

    fun requestNextPermissionStep() {
        if (activePermissionRequest != PermissionRequest.NONE) return
        val state = readPermissionState()
        if (!state.isDefaultSmsApp) return

        if (state.missingSmsPermissions.isNotEmpty()) {
            if (!smsPermissionsAttemptedForProcess) {
                launchSmsPermissionRequest(state.missingSmsPermissions)
            }
            return
        }

        if (!state.contactsGranted && !contactsStepResolvedForProcess) {
            if (!contactsPermissionAttemptedForProcess) {
                contactsPermissionAttemptedForProcess = true
                activePermissionRequest = PermissionRequest.CONTACTS
                AdRuntime.suppressNextAppOpen()
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !state.notificationsGranted &&
            !notificationStepResolvedForProcess &&
            !notificationPermissionAttemptedForProcess
        ) {
            notificationPermissionAttemptedForProcess = true
            activePermissionRequest = PermissionRequest.NOTIFICATIONS
            AdRuntime.suppressNextAppOpen()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun onboardingPermissionStepsComplete(state: AppPermissionState): Boolean =
        state.hasCoreMessagingAccess && activePermissionRequest == PermissionRequest.NONE &&
            (state.contactsGranted || contactsStepResolvedForProcess) &&
            (state.notificationsGranted || notificationStepResolvedForProcess)

    fun requestSmsPermissionsFromUser() {
        if (activePermissionRequest != PermissionRequest.NONE) return
        val state = readPermissionState()
        if (!state.isDefaultSmsApp || state.missingSmsPermissions.isEmpty()) return
        val runtimeDialogMayBeAvailable = state.missingSmsPermissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }
        if (smsPermissionsAttemptedForProcess && !runtimeDialogMayBeAvailable) {
            showSmsSettingsPrompt = true
        } else {
            launchSmsPermissionRequest(state.missingSmsPermissions)
        }
    }

    fun dismissSmsSettingsPrompt() {
        showSmsSettingsPrompt = false
    }

    fun openSmsSettings() {
        showSmsSettingsPrompt = false
        AdRuntime.suppressNextAppOpen()
        openApplicationDetailsSettings()
    }

    fun dismissContactsSettingsPrompt() {
        showContactsSettingsPrompt = false
        contactsStepResolvedForProcess = true
        refreshPermissionState(forceEmit = true)
    }

    fun openContactsSettings() {
        showContactsSettingsPrompt = false
        contactsStepResolvedForProcess = true
        AdRuntime.suppressNextAppOpen()
        openApplicationDetailsSettings()
    }

    fun dismissNotificationSettingsPrompt() {
        showNotificationSettingsPrompt = false
        notificationStepResolvedForProcess = true
    }

    fun openNotificationSettings() {
        showNotificationSettingsPrompt = false
        notificationStepResolvedForProcess = true
        AdRuntime.suppressNextAppOpen()
        runCatching {
            MessageNotificationManager.openAppNotificationSettings(this)
        }.onFailure {
            openApplicationDetailsSettings()
        }
    }

    private fun launchSmsPermissionRequest(missingPermissions: List<String>) {
        smsPermissionsAttemptedForProcess = true
        activePermissionRequest = PermissionRequest.SMS
        AdRuntime.suppressNextAppOpen()
        smsPermissionsLauncher.launch(missingPermissions.toTypedArray())
    }

    private fun refreshPermissionState(forceEmit: Boolean = false): AppPermissionState {
        val state = readPermissionState()
        resetSessionStepsForExternalChanges(state)
        val current = _permissionState.value
        val unchanged =
            current.isDefaultSmsApp == state.isDefaultSmsApp &&
                current.missingSmsPermissions == state.missingSmsPermissions &&
                current.contactsGranted == state.contactsGranted &&
                current.notificationsGranted == state.notificationsGranted
        val updated = state.copy(
            revision = if (forceEmit || !unchanged) current.revision + 1L else current.revision
        )
        if (forceEmit || !unchanged) _permissionState.value = updated
        return updated
    }

    private fun readPermissionState(): AppPermissionState {
        val missingSmsPermissions = REQUIRED_SMS_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        return AppPermissionState(
            isDefaultSmsApp = DefaultSmsManager(this).isDefaultSmsApp(),
            missingSmsPermissions = missingSmsPermissions,
            contactsGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED,
            notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    private fun resetSessionStepsForExternalChanges(state: AppPermissionState) {
        if (lastObservedSmsRole == true && !state.isDefaultSmsApp) {
            smsPermissionsAttemptedForProcess = false
            showSmsSettingsPrompt = false
            showContactsSettingsPrompt = false
            showNotificationSettingsPrompt = false
        }
        if (lastObservedMissingSmsPermissions?.isEmpty() == true && state.missingSmsPermissions.isNotEmpty()) {
            smsPermissionsAttemptedForProcess = false
            showSmsSettingsPrompt = false
            showContactsSettingsPrompt = false
            showNotificationSettingsPrompt = false
        }
        if (lastObservedContactsGranted == true && !state.contactsGranted) {
            contactsPermissionAttemptedForProcess = false
            contactsStepResolvedForProcess = false
            showContactsSettingsPrompt = false
        }
        if (lastObservedNotificationsGranted == true && !state.notificationsGranted) {
            notificationPermissionAttemptedForProcess = false
            notificationStepResolvedForProcess = false
            showNotificationSettingsPrompt = false
        }
        lastObservedSmsRole = state.isDefaultSmsApp
        lastObservedMissingSmsPermissions = state.missingSmsPermissions.toSet()
        lastObservedContactsGranted = state.contactsGranted
        lastObservedNotificationsGranted = state.notificationsGranted
    }

    private fun openApplicationDetailsSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
    }

    private fun captureNavigationIntent(source: Intent) {
        val notificationThreadId = source.getLongExtra(
            MessageNotificationManager.EXTRA_THREAD_ID,
            0L
        )
        val isNotificationTap =
            source.action == MessageNotificationManager.ACTION_OPEN_CHAT || notificationThreadId > 0L
        val navigationToken = source.getStringExtra(
            MessageNotificationManager.EXTRA_NAVIGATION_TOKEN
        ).orEmpty()
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

        val duplicateNotificationIntent = isNotificationTap && navigationToken.isNotBlank() &&
            synchronized(handledNavigationTokens) {
                navigationToken in handledNavigationTokens ||
                    _pendingChatDestination.value?.navigationToken == navigationToken
            }

        if (duplicateNotificationIntent) {
            clearConsumedNavigationExtras(source)
            return
        }

        if ((notificationThreadId > 0L || sendToAddress.isNotBlank()) && address.isNotBlank()) {
            if (isNotificationTap && notificationThreadId > 0L) {
                MessageNotificationManager.consumeThread(this, notificationThreadId)
            }
            _pendingChatDestination.value = PendingChatDestination(
                threadId = notificationThreadId,
                address = address,
                contactName = source.getStringExtra(
                    MessageNotificationManager.EXTRA_CONTACT_NAME
                ).orEmpty().ifBlank { address },
                navigationToken = navigationToken
            )
        }

        clearConsumedNavigationExtras(source)
    }

    private fun clearConsumedNavigationExtras(source: Intent) {
        source.removeExtra(MessageNotificationManager.EXTRA_THREAD_ID)
        source.removeExtra(MessageNotificationManager.EXTRA_ADDRESS)
        source.removeExtra(MessageNotificationManager.EXTRA_CONTACT_NAME)
        source.removeExtra(MessageNotificationManager.EXTRA_NAVIGATION_TOKEN)
        if (source.action == MessageNotificationManager.ACTION_OPEN_CHAT || source.action == Intent.ACTION_SENDTO) {
            source.data = null
        }
    }

    companion object {
        private val REQUIRED_SMS_PERMISSIONS = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        private var activePermissionRequest = PermissionRequest.NONE
        private var smsPermissionsAttemptedForProcess = false
        private var contactsPermissionAttemptedForProcess = false
        private var contactsStepResolvedForProcess = false
        private var notificationPermissionAttemptedForProcess = false
        private var notificationStepResolvedForProcess = false
        private var lastObservedSmsRole: Boolean? = null
        private var lastObservedMissingSmsPermissions: Set<String>? = null
        private var lastObservedContactsGranted: Boolean? = null
        private var lastObservedNotificationsGranted: Boolean? = null
        private var onboardingAdOpportunityConsumed = false
        private const val MIN_WARM_RESUME_BACKGROUND_MILLIS = 5_000L
        private const val MAX_HANDLED_NAVIGATION_TOKENS = 64
        private val handledNavigationTokens = linkedSetOf<String>()
    }

    private enum class PermissionRequest {
        NONE,
        SMS,
        CONTACTS,
        NOTIFICATIONS
    }
}
