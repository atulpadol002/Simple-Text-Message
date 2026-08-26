package com.ap.simpletextmessage.data.model

data class ScheduledSms(
    val id: Long,
    val contactName: String = "",
    val phoneNumber: String,
    val message: String,
    val scheduledTime: Long,
    val status: ScheduledSmsStatus = ScheduledSmsStatus.SCHEDULED
)

enum class ScheduledSmsStatus {
    SCHEDULED,
    SENDING,
    SENT,
    FAILED
}
