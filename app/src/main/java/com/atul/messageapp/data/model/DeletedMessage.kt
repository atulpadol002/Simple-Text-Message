package com.atul.messageapp.data.model

data class DeletedMessage(
    val localMessageId: Long = 0L,
    val recycleBinId: Long = 0L,
    val originalMessageId: Long,
    val originalThreadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val sentDate: Long? = null,
    val type: Int,
    val read: Boolean,
    val seen: Boolean,
    val status: Int? = null,
    val serviceCenter: String? = null,
    val subscriptionId: Int? = null,
    val restored: Boolean = false
)
