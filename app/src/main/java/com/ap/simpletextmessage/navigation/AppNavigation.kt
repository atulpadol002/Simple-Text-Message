package com.ap.simpletextmessage.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import android.provider.Telephony
import com.ap.simpletextmessage.AppPermissionState
import com.ap.simpletextmessage.MainActivity
import com.ap.simpletextmessage.PendingChatDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ap.simpletextmessage.theme.ThemeMode
import com.ap.simpletextmessage.ui.about.AboutScreen
import com.ap.simpletextmessage.ui.chat.ChatScreen
import com.ap.simpletextmessage.ui.home.HomeScreen
import com.ap.simpletextmessage.ui.newmessage.NewMessageScreen
import com.ap.simpletextmessage.ui.permission.PermissionScreen
import com.ap.simpletextmessage.ui.premium.PaywallScreen
import com.ap.simpletextmessage.ui.recyclebin.RecycleBinScreen
import com.ap.simpletextmessage.ui.splash.SplashScreen
import com.ap.simpletextmessage.ui.theme.ThemeScreen
import com.ap.simpletextmessage.ui.archive.ArchiveChatsScreen
import com.ap.simpletextmessage.ui.scheduled.ScheduledSmsScreen
import com.ap.simpletextmessage.ui.blocked.BlockedNumbersScreen
import com.ap.simpletextmessage.ui.starred.StarredMessagesScreen
import com.ap.simpletextmessage.ui.language.LanguageScreen
import com.ap.simpletextmessage.ads.InterstitialAdManager
import com.ap.simpletextmessage.ads.AutoInterstitialEvent
import com.ap.simpletextmessage.ads.AutoInterstitialManager
import com.ap.simpletextmessage.ads.AdRemoteConfigManager
import com.ap.simpletextmessage.localization.LanguagePreferences
import com.ap.simpletextmessage.localization.LanguageFlowPolicy
import com.ap.simpletextmessage.localization.LanguageScreenOrigin
import com.ap.simpletextmessage.R


@Composable
fun AppNavigation(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit
) {

    val navController =
        rememberNavController()

    val context = LocalContext.current
    val languagePreferences = remember(context) { LanguagePreferences(context) }
    val showSecondSessionLanguage = remember {
        LanguageFlowPolicy.shouldShowSecondSession(
            AutoInterstitialManager.currentSessionNumber(),
            languagePreferences.isLanguageOnboardingShown()
        )
    }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val activity = context as? MainActivity
    val pendingDestinationFlow = remember(activity) {
        activity?.pendingChatDestination ?: MutableStateFlow(null)
    }
    val pendingDestination by pendingDestinationFlow.collectAsState()
    val permissionStateFlow = remember(activity) {
        activity?.permissionState ?: MutableStateFlow(AppPermissionState())
    }
    val permissionState by permissionStateFlow.collectAsState()
    val adConfig by AdRemoteConfigManager.config.collectAsState()
    val paywallEnabled by AdRemoteConfigManager.paywallEnabled.collectAsState()
    val initialPendingDestination = remember(activity) {
        activity?.pendingChatDestination?.value
    }
    val initialChatRoute = remember(
        initialPendingDestination,
        permissionState.hasCoreMessagingAccess
    ) {
        initialPendingDestination
            ?.takeIf {
                it.threadId > 0L && permissionState.hasCoreMessagingAccess
            }
            ?.toChatRoute()
    }

    val currentRoute =
        navController.currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route

    val navigationInProgress = remember { mutableStateOf(false) }

    LaunchedEffect(
        currentRoute,
        adConfig.masterEnabled,
        adConfig.onboardingGetStartedNative.enabled,
        adConfig.defaultSmsNative.enabled
    ) {
        val onboardingNativeEnabled = when (currentRoute) {
            Routes.Splash.route -> adConfig.onboardingGetStartedNative.enabled
            Routes.Permission.route -> adConfig.defaultSmsNative.enabled
            else -> false
        }
        if (adConfig.masterEnabled && onboardingNativeEnabled) {
            activity?.requestOnboardingNativeAdsConsent()
        }
    }

    fun runEligibleAdEvent(
        event: AutoInterstitialEvent,
        allowNormalInterstitial: Boolean = false,
        proceed: () -> Unit
    ) {
        val hostActivity = activity
        if (hostActivity == null) {
            proceed()
            return
        }
        InterstitialAdManager.onEligibleTransition(
            activity = hostActivity,
            activitySafe = hostActivity.isAdPresentationSafe(),
            event = event,
            allowNormalInterstitial = allowNormalInterstitial,
            proceed = proceed
        )
    }

    LaunchedEffect(permissionState.revision, currentRoute) {
        val hostActivity = activity ?: return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect

        if (!permissionState.hasCoreMessagingAccess) {
            if (route == Routes.Permission.route) {
                if (permissionState.isDefaultSmsApp) {
                    hostActivity.requestNextPermissionStep()
                }
                return@LaunchedEffect
            }
            if (route != Routes.Splash.route && route != Routes.Language.route) {
                val hasHomeDestination = runCatching {
                    navController.getBackStackEntry(Routes.Home.route)
                }.isSuccess
                navController.navigate(Routes.Permission.route) {
                    launchSingleTop = true
                    if (hasHomeDestination) {
                        popUpTo(Routes.Home.route) { inclusive = true }
                    } else {
                        popUpTo(route) { inclusive = true }
                    }
                }
            }
            return@LaunchedEffect
        }

        if (route == Routes.Permission.route) {
            navController.navigate(Routes.Home.route) {
                popUpTo(Routes.Permission.route) { inclusive = true }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }

        if (route != Routes.Splash.route) {
            hostActivity.requestNextPermissionStep()
        }
    }

    LaunchedEffect(pendingDestination, currentRoute) {
        val destination = pendingDestination ?: return@LaunchedEffect
        val hostActivity = activity ?: return@LaunchedEffect

        if (
            initialChatRoute != null &&
            destination == initialPendingDestination &&
            currentRoute == Routes.Chat.route
        ) {
            hostActivity.consumePendingChat(destination)
            return@LaunchedEffect
        }

        if (
            currentRoute == null ||
            currentRoute == Routes.Splash.route ||
            currentRoute == Routes.Permission.route ||
            !permissionState.hasCoreMessagingAccess
        ) return@LaunchedEffect

        val resolvedDestination = if (destination.threadId > 0L) {
            destination
        } else {
            val threadId = withContext(Dispatchers.IO) {
                Telephony.Threads.getOrCreateThreadId(context, destination.address)
            }
            destination.copy(threadId = threadId)
        }
        if (resolvedDestination.threadId <= 0L) return@LaunchedEffect

        val route = resolvedDestination.toChatRoute()
        val currentEntry = navController.currentBackStackEntry
        val alreadyShowingDestination =
            currentRoute == Routes.Chat.route &&
                currentEntry?.arguments?.getString("conversationId")?.toLongOrNull() ==
                resolvedDestination.threadId

        if (!alreadyShowingDestination) {
            val hasHomeDestination = runCatching {
                navController.getBackStackEntry(Routes.Home.route)
            }.isSuccess
            navController.navigate(route) {
                launchSingleTop = true
                if (hasHomeDestination) {
                    popUpTo(Routes.Home.route) { inclusive = false }
                } else if (currentRoute == Routes.Chat.route) {
                    popUpTo(Routes.Chat.route) { inclusive = true }
                }
            }
        }
        hostActivity.consumePendingChat(destination)
    }

    NavHost(
        navController = navController,
        startDestination = initialChatRoute ?: Routes.Splash.route,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {

        composable(
            Routes.Splash.route
        ) {
            SplashScreen(
                onPermissionFlow = {
                    navController.navigate(
                        if (showSecondSessionLanguage) {
                            Routes.Language.create(LanguageScreenOrigin.SECOND_SESSION.routeValue)
                        } else {
                            Routes.Permission.route
                        }
                    )
                },
                onDirectHome = {
                    navController.navigate(
                        if (showSecondSessionLanguage) {
                            Routes.Language.create(LanguageScreenOrigin.SECOND_SESSION.routeValue)
                        } else if (permissionState.hasCoreMessagingAccess) {
                            Routes.Home.route
                        } else {
                            Routes.Permission.route
                        }
                    ) {
                        popUpTo(
                            Routes.Splash.route
                        ) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(
            Routes.Permission.route
        ) {
            PermissionScreen(
                isDefaultSmsApp = permissionState.isDefaultSmsApp,
                missingSmsPermissions = permissionState.missingSmsPermissions,
                onPermissionStateChanged = activity?.let { hostActivity ->
                    hostActivity::refreshPermissionsAfterRoleRequest
                } ?: {},
                onRequestSmsPermissions = activity?.let { hostActivity ->
                    hostActivity::requestSmsPermissionsFromUser
                } ?: {}
            )
        }

        composable(
            Routes.Home.route
        ) {
            HomeScreen(
                isActive = currentRoute == Routes.Home.route,
                navigationInProgress = navigationInProgress.value,
                contactsPermissionGranted = permissionState.contactsGranted,
                onHomeResumed = { navigationInProgress.value = false },
                onNewMessageClick = {
                    if (navController.currentDestination?.route == Routes.Home.route) {
                        runEligibleAdEvent(AutoInterstitialEvent.IN_APP_TAP) {
                            navController.navigate(Routes.NewMessage.route) {
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onConversationClick = {
                        conversationId,
                        name,
                        phoneNumber ->

                    if (navController.currentDestination?.route == Routes.Home.route) {
                        navigationInProgress.value = true
                        runEligibleAdEvent(AutoInterstitialEvent.HOME_TAP) {
                            navController.navigate(
                                "chat/$conversationId/" +
                                    "${Uri.encode(name)}/" + Uri.encode(phoneNumber)
                            )
                        }
                    }
                },
                onPremiumClick = {
                    if (
                        paywallEnabled &&
                        navController.currentDestination?.route == Routes.Home.route
                    ) {
                        navigationInProgress.value = true
                        navController.navigate(Routes.Paywall.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onDrawerNavigate = { route ->
                    if (navController.currentDestination?.route == Routes.Home.route) {
                        navigationInProgress.value = true
                        val navigate = { navController.navigate(route) }
                        val normalInterstitialRoute = route in setOf(
                                Routes.ArchiveChats.route,
                                Routes.StarredMessages.route,
                                Routes.BlockNumbers.route,
                                Routes.RecycleBin.route
                            )
                        runEligibleAdEvent(
                            event = AutoInterstitialEvent.IN_APP_TAP,
                            allowNormalInterstitial = normalInterstitialRoute,
                            proceed = navigate
                        )
                    }
                }
            )
        }

        composable(Routes.Paywall.route) {
            LaunchedEffect(paywallEnabled) {
                if (!paywallEnabled) navController.popBackStack()
            }
            PaywallScreen(onBackClick = { navController.popBackStack() })
        }

        composable(Routes.About.route) {
            AboutScreen(onBackClick = { navController.popBackStack() })
        }

        composable(Routes.Language.route) { backStackEntry ->
            val origin = LanguageScreenOrigin.fromRouteValue(
                backStackEntry.arguments?.getString("origin")
            )
            fun finishSecondSession() {
                languagePreferences.markLanguageOnboardingShown()
                navController.navigate(
                    if (permissionState.hasCoreMessagingAccess) {
                        Routes.Home.route
                    } else {
                        Routes.Permission.route
                    }
                ) {
                    popUpTo(Routes.Splash.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
            LanguageScreen(
                initialLanguage = languagePreferences.selectedLanguage(),
                onBack = {
                    if (origin == LanguageScreenOrigin.SECOND_SESSION) {
                        finishSecondSession()
                    } else {
                        navController.popBackStack()
                    }
                },
                onDone = { selectedLanguage ->
                    languagePreferences.saveSelectedLanguage(selectedLanguage)
                    val complete = {
                        if (origin == LanguageScreenOrigin.SECOND_SESSION) {
                            languagePreferences.markLanguageOnboardingShown()
                        }
                        languagePreferences.applySelectedLanguage(selectedLanguage)
                        if (origin == LanguageScreenOrigin.SECOND_SESSION) {
                            finishSecondSession()
                        } else {
                            navController.popBackStack()
                        }
                        Unit
                    }
                    if (LanguageFlowPolicy.shouldShowDoneInterstitial(origin)) {
                        runEligibleAdEvent(
                            event = AutoInterstitialEvent.IN_APP_TAP,
                            allowNormalInterstitial = true,
                            proceed = complete
                        )
                    } else {
                        complete()
                    }
                }
            )
        }

        composable(
            Routes.NewMessage.route
        ) { backStackEntry ->
            val backHandled = androidx.compose.runtime.remember(backStackEntry) {
                androidx.compose.runtime.mutableStateOf(false)
            }
            val requestBack = back@{
                    if (backHandled.value || navController.currentDestination?.route != Routes.NewMessage.route) {
                        return@back
                    }
                    backHandled.value = true
                    runEligibleAdEvent(AutoInterstitialEvent.BACK_TAP) {
                        if (!navController.popBackStack()) {
                            backHandled.value = false
                        }
                    }
                }
            BackHandler(enabled = !backHandled.value && !imeVisible) { requestBack() }
            NewMessageScreen(
                onBackClick = requestBack,
                onContactClick = { threadId, name, phone ->
                    runEligibleAdEvent(AutoInterstitialEvent.IN_APP_TAP) {
                        navController.navigate(
                            "chat/$threadId/" + "${Uri.encode(name)}/" + Uri.encode(phone)
                        )
                    }
                }
            )
        }

        composable(
            Routes.Chat.route
        ) { backStackEntry ->

            val backHandled = remember(backStackEntry) { mutableStateOf(false) }
            fun leaveChat(popToHome: Boolean, countBackTap: Boolean) {
                if (
                    backHandled.value ||
                    navController.currentDestination?.route != Routes.Chat.route
                ) return

                backHandled.value = true
                navigationInProgress.value = true
                val proceed = {
                    val popped = if (popToHome) {
                        navController.popBackStack(Routes.Home.route, inclusive = false)
                    } else {
                        navController.popBackStack()
                    }
                    if (!popped) {
                        navController.navigate(Routes.Home.route) {
                            popUpTo(Routes.Chat.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                if (countBackTap) runEligibleAdEvent(AutoInterstitialEvent.BACK_TAP, proceed = proceed)
                else proceed()
            }

            val conversationId =
                backStackEntry.arguments
                    ?.getString("conversationId")
                    ?.toLongOrNull()
                    ?: 0L

            val name =
                backStackEntry.arguments
                    ?.getString("name")
                    ?.let(Uri::decode)
                    ?: ""

            val phoneNumber =
                backStackEntry.arguments
                    ?.getString("phoneNumber")
                    ?.let(Uri::decode)
                    ?: ""

            ChatScreen(
                contactName = name,
                phoneNumber = phoneNumber,
                conversationId = conversationId,
                onBackClick = { leaveChat(popToHome = false, countBackTap = true) },
                onConversationDeleted = { leaveChat(popToHome = true, countBackTap = false) }
            )
        }

        composable(
            Routes.ArchiveChats.route
        ) {
            val goBack = {
                runEligibleAdEvent(AutoInterstitialEvent.BACK_TAP) {
                    navController.popBackStack()
                }
            }
            BackHandler(enabled = !imeVisible) { goBack() }
            ArchiveChatsScreen(
                onBackClick = goBack,
                onConversationClick = {
                        conversationId,
                        name,
                        phoneNumber ->

                    runEligibleAdEvent(AutoInterstitialEvent.IN_APP_TAP) {
                        navController.navigate(
                            "chat/$conversationId/" +
                                "${Uri.encode(name)}/" + Uri.encode(phoneNumber)
                        )
                    }
                }
            )
        }

        composable(
            Routes.Theme.route
        ) {
            val goBack = {
                runEligibleAdEvent(AutoInterstitialEvent.BACK_TAP) {
                    navController.popBackStack()
                }
            }
            BackHandler(enabled = !imeVisible) { goBack() }
            ThemeScreen(
                selectedTheme = themeMode,
                onThemeSelected = onThemeSelected,
                onBackClick = goBack
            )
        }

        composable(
            Routes.ScheduledSms.route
        ) {
            val goBack = {
                runEligibleAdEvent(AutoInterstitialEvent.BACK_TAP) {
                    navController.popBackStack()
                }
            }
            BackHandler(enabled = !imeVisible) { goBack() }
            ScheduledSmsScreen(
                onBackClick = goBack
            )
        }

        composable(
            Routes.BlockNumbers.route
        ) {
            val goBack = {
                runEligibleAdEvent(AutoInterstitialEvent.BACK_TAP) {
                    navController.popBackStack()
                }
            }
            BackHandler(enabled = !imeVisible) { goBack() }
            BlockedNumbersScreen(
                onBackClick = goBack
            )
        }

        composable(
            Routes.StarredMessages.route
        ) {
            val goBack = {
                runEligibleAdEvent(AutoInterstitialEvent.BACK_TAP) {
                    navController.popBackStack()
                }
            }
            BackHandler(enabled = !imeVisible) { goBack() }
            StarredMessagesScreen(
                onBackClick = goBack,
                onMessageClick = { conversationId, name, phoneNumber ->
                    runEligibleAdEvent(AutoInterstitialEvent.IN_APP_TAP) {
                        navController.navigate(
                            "chat/$conversationId/${Uri.encode(name)}/${Uri.encode(phoneNumber)}"
                        )
                    }
                }
            )
        }

        composable(
            Routes.RecycleBin.route
        ) {
            val goBack = {
                runEligibleAdEvent(AutoInterstitialEvent.BACK_TAP) {
                    navController.popBackStack(Routes.Home.route, inclusive = false)
                }
            }
            BackHandler(enabled = !imeVisible) { goBack() }

            RecycleBinScreen(
                onBackClick = goBack
            )
        }
    }

    if (activity?.showSmsSettingsPrompt == true) {
        AlertDialog(
            onDismissRequest = activity::dismissSmsSettingsPrompt,
            title = { Text(stringResource(R.string.allow_sms_access_title)) },
            text = {
                Text(
                    stringResource(R.string.allow_sms_access_settings_message)
                )
            },
            confirmButton = {
                TextButton(onClick = activity::openSmsSettings) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = activity::dismissSmsSettingsPrompt) {
                    Text(stringResource(R.string.not_now))
                }
            }
        )
    } else if (activity?.showContactsSettingsPrompt == true) {
        AlertDialog(
            onDismissRequest = activity::dismissContactsSettingsPrompt,
            title = { Text(stringResource(R.string.allow_contacts_access_title)) },
            text = {
                Text(
                    stringResource(R.string.allow_contacts_access_settings_message)
                )
            },
            confirmButton = {
                TextButton(onClick = activity::openContactsSettings) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = activity::dismissContactsSettingsPrompt) {
                    Text(stringResource(R.string.not_now))
                }
            }
        )
    } else if (activity?.showNotificationSettingsPrompt == true) {
        AlertDialog(
            onDismissRequest = activity::dismissNotificationSettingsPrompt,
            title = { Text(stringResource(R.string.enable_notifications_title)) },
            text = {
                Text(
                    stringResource(R.string.enable_notifications_message)
                )
            },
            confirmButton = {
                TextButton(onClick = activity::openNotificationSettings) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = activity::dismissNotificationSettingsPrompt) {
                    Text(stringResource(R.string.not_now))
                }
            }
        )
    }
}

private fun PendingChatDestination.toChatRoute(): String =
    "chat/$threadId/${Uri.encode(contactName.ifBlank { address })}/${Uri.encode(address)}"
