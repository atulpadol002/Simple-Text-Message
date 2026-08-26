package com.ap.simpletextmessage.data.model

data class DeletedConversation(
    val recycleBinId: Long = 0L,
    val originalThreadId: Long,
    val address: String,
    val cachedDisplayName: String? = null,
    val deletedAt: Long
)
