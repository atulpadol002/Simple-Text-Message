package com.ap.simpletextmessage.data.model

data class Conversation(

    val id: Long,

    val phoneNumber: String,

    val name: String,

    val lastMessage: String,

    val lastMessageTime: Long,

    val unreadCount: Int

)