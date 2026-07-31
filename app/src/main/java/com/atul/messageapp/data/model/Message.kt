package com.atul.messageapp.data.model

data class Message(

    val id: Long,

    val conversationId: Long,

    val phoneNumber: String,

    val body: String,

    val timestamp: Long,

    val isIncoming: Boolean,

    val isRead: Boolean,

    val status: MessageStatus = MessageStatus.NONE

)