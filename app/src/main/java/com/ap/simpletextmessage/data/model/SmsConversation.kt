package com.ap.simpletextmessage.data.model

data class SmsConversation(
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val unreadCount: Int
)