package com.ap.messages.data.model

data class ScheduledSms(
    val id: Long,
    val contactName: String = "",
    val phoneNumber: String,
    val message: String,
    val scheduledTime: Long
)