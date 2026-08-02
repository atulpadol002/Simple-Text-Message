package com.atul.messageapp.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    val currentRoute =
        navController.currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route

    val navigationInProgress = remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = Routes.Splash.route,
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
                        Routes.Home.route
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
                onPermissionGranted = {
                    navController.navigate(
                        Routes.Home.route
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
                    navigationInProgress.value = false
                    backHandled.value = false
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
}
