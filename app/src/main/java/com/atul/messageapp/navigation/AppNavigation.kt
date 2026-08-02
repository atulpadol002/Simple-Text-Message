package com.atul.messageapp.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
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

    NavHost(
        navController = navController,
        startDestination = Routes.Splash.route
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
                onNewMessageClick = {
                    navController.navigate(
                        Routes.NewMessage.route
                    )
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
                },
                onDrawerNavigate = { route ->
                    navController.navigate(route)
                }
            )
        }

        composable(
            Routes.NewMessage.route
        ) {
            NewMessageScreen(
                onBackClick = { navController.popBackStack() },
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

            BackHandler {
                navController.popBackStack(
                    Routes.Home.route,
                    inclusive = false
                )
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
                onBackClick = {
                    navController.popBackStack()
                }
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
