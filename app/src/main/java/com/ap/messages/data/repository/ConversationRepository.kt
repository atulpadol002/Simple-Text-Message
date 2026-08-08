package com.ap.messages.data.repository

import android.content.Context
import com.ap.messages.data.datasource.ConversationDataSource
import com.ap.messages.data.model.Conversation

class ConversationRepository(
    private val context: Context
) {

    private val dataSource = ConversationDataSource(context)

    fun getConversations(): List<Conversation> {
        return dataSource.getConversations()
    }

}