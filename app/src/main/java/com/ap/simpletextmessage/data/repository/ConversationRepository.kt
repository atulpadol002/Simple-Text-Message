package com.ap.simpletextmessage.data.repository

import android.content.Context
import com.ap.simpletextmessage.data.datasource.ConversationDataSource
import com.ap.simpletextmessage.data.model.Conversation

class ConversationRepository(
    private val context: Context
) {

    private val dataSource = ConversationDataSource(context)

    fun getConversations(): List<Conversation> {
        return dataSource.getConversations()
    }

}