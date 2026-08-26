package com.ap.simpletextmessage.data.model

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int
)