package com.ap.simpletextmessage.navigation

sealed class Routes(
    val route: String
) {

    object Splash : Routes("splash")

    object Permission : Routes("permission")

    object Home : Routes("home")

    object Paywall : Routes("paywall")

    object About : Routes("about")

    object NewMessage : Routes("new_message")

    object Chat :
        Routes("chat/{conversationId}/{name}/{phoneNumber}")

    object ArchiveChats :
        Routes("archive_chats")

    object Theme :
        Routes("theme")

    object Language : Routes("language/{origin}") {
        fun create(origin: String): String = "language/$origin"
    }

    object ScheduledSms :
        Routes("scheduled_sms")

    object BlockNumbers :
        Routes("block_numbers")

    object StarredMessages :
        Routes("starred_messages")

    object RecycleBin :
        Routes("recycle_bin")
}
