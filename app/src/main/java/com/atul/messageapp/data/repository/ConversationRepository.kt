package com.atul.messageapp.data.repository

import android.content.Context
import com.atul.messageapp.data.datasource.ConversationDataSource
import com.atul.messageapp.data.model.Conversation

class ConversationRepository(
    private val context: Context
) {

    private val dataSource = ConversationDataSource(context)

    fun getConversations(): List<Conversation> {
        return dataSource.getConversations()
    }

}