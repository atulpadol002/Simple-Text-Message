package com.atul.messageapp.navigation

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
import android.provider.Telephony
import com.atul.messageapp.AppPermissionState
import com.atul.messageapp.MainActivity
import com.atul.messageapp.PendingChatDestination
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
import com.atul.messageapp.theme.ThemeMode
import com.atul.messageapp.ui.chat.ChatScreen
import com.atul.messageapp.ui.feature.FeatureScreen
import com.atul.messageapp.ui.home.HomeScreen
import com.atul.messageapp.ui.newmessage.NewMessageScreen
import com.atul.messageapp.ui.permission.PermissionScreen
import com.atul.messageapp.ui.recyclebin.RecycleBinScreen
import com.atul.messageapp.ui.splash.SplashScreen
import com.atul.messageapp.ui.theme.ThemeScreen
import com.atul.messageapp.ui.archive.ArchiveChatsScreen
import com.atul.messageapp.ui.scheduled.ScheduledSmsScreen
import com.atul.messageapp.ui.blocked.BlockedNumbersScreen
import com.atul.messageapp.ui.starred.StarredMessagesScreen


@Composable
fun AppNavigation(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit
) {

    val navController =
        rememberNavController()

    val context = LocalContext.current
    val activity = context as? MainActivity
    val pendingDestinationFlow = remember(activity) {
        activity?.pendingChatDestination ?: MutableStateFlow(null)
    }
    val pendingDestination by pendingDestinationFlow.collectAsState()
    val permissionStateFlow = remember(activity) {
        activity?.permissionState ?: MutableStateFlow(AppPermissionState())
    }
    val permissionState by permissionStateFlow.collectAsState()
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
            if (route != Routes.Splash.route) {
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
                        Routes.Permission.route
                    )
                },
                onDirectHome = {
                    navController.navigate(
                        if (permissionState.hasCoreMessagingAccess) {
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
                onHomeResumed = { navigationInProgress.value = false },
                onNewMessageClick = {
                    if (navController.currentDestination?.route == Routes.Home.route) {
                        navController.navigate(Routes.NewMessage.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onConversationClick = {
                        conversationId,
                        name,
                        phoneNumber ->

                    if (navController.currentDestination?.route == Routes.Home.route) {
                        navigationInProgress.value = true
                        navController.navigate(
                            "chat/$conversationId/" +
                                    "${Uri.encode(name)}/" +
                                    Uri.encode(phoneNumber)
                        )
                    }
                },
                onDrawerNavigate = { route ->
                    if (navController.currentDestination?.route == Routes.Home.route) {
                        navigationInProgress.value = true
                        navController.navigate(route)
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
            NewMessageScreen(
                onBackClick = back@{
                    if (backHandled.value || navController.currentDestination?.route != Routes.NewMessage.route) {
                        return@back
                    }
                    if (navController.previousBackStackEntry?.destination?.route != Routes.Home.route) {
                        return@back
                    }
                    backHandled.value = true
                    if (!navController.popBackStack()) {
                        backHandled.value = false
                    }
                },
                onContactClick = { name, phone ->
                    navController.navigate(
                        "chat/0/" +
                                "${Uri.encode(name)}/" +
                                Uri.encode(phone)
                    )
                }
            )
        }

        composable(
            Routes.Chat.route
        ) { backStackEntry ->

            val backHandled = remember(backStackEntry) { mutableStateOf(false) }
            fun leaveChat(popToHome: Boolean) {
                if (
                    backHandled.value ||
                    navController.currentDestination?.route != Routes.Chat.route
                ) return

                backHandled.value = true
                navigationInProgress.value = true
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

            BackHandler(enabled = !backHandled.value) { leaveChat(popToHome = false) }

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
                onBackClick = { leaveChat(popToHome = false) },
                onConversationDeleted = { leaveChat(popToHome = true) }
            )
        }

        composable(
            Routes.ArchiveChats.route
        ) {
            ArchiveChatsScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onConversationClick = {
                        conversationId,
                        name,
                        phoneNumber ->

                    navController.navigate(
                        "chat/$conversationId/" +
                                "${Uri.encode(name)}/" +
                                Uri.encode(phoneNumber)
                    )
                }
            )
        }

        composable(
            Routes.Theme.route
        ) {
            ThemeScreen(
                selectedTheme = themeMode,
                onThemeSelected = onThemeSelected,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            Routes.ScheduledSms.route
        ) {

            ScheduledSmsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            Routes.BlockNumbers.route
        ) {
            BlockedNumbersScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            Routes.StarredMessages.route
        ) {
            StarredMessagesScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onMessageClick = { conversationId, name, phoneNumber ->
                    navController.navigate(
                        "chat/$conversationId/${Uri.encode(name)}/${Uri.encode(phoneNumber)}"
                    )
                }
            )
        }

        composable(
            Routes.RecycleBin.route
        ) {
            BackHandler {
                navController.popBackStack(
                    Routes.Home.route,
                    inclusive = false
                )
            }

            RecycleBinScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }

    if (activity?.showSmsSettingsPrompt == true) {
        AlertDialog(
            onDismissRequest = activity::dismissSmsSettingsPrompt,
            title = { Text("Allow SMS access") },
            text = {
                Text(
                    "To send and receive messages, allow the required SMS permissions in system settings."
                )
            },
            confirmButton = {
                TextButton(onClick = activity::openSmsSettings) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = activity::dismissSmsSettingsPrompt) {
                    Text("Not now")
                }
            }
        )
    } else if (activity?.showContactsSettingsPrompt == true) {
        AlertDialog(
            onDismissRequest = activity::dismissContactsSettingsPrompt,
            title = { Text("Allow contacts access") },
            text = {
                Text(
                    "To show contact names and photos, allow Contacts permission in system settings."
                )
            },
            confirmButton = {
                TextButton(onClick = activity::openContactsSettings) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = activity::dismissContactsSettingsPrompt) {
                    Text("Not now")
                }
            }
        )
    } else if (activity?.showNotificationSettingsPrompt == true) {
        AlertDialog(
            onDismissRequest = activity::dismissNotificationSettingsPrompt,
            title = { Text("Enable notifications") },
            text = {
                Text(
                    "Allow notifications to receive incoming message alerts."
                )
            },
            confirmButton = {
                TextButton(onClick = activity::openNotificationSettings) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = activity::dismissNotificationSettingsPrompt) {
                    Text("Not now")
                }
            }
        )
    }
}

private fun PendingChatDestination.toChatRoute(): String =
    "chat/$threadId/${Uri.encode(contactName.ifBlank { address })}/${Uri.encode(address)}"
