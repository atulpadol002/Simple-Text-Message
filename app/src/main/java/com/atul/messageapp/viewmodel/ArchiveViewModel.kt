package com.atul.messageapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.model.SmsConversation
import com.atul.messageapp.data.preferences.ArchivePreferences
import com.atul.messageapp.data.repository.SmsRepository
import com.atul.messageapp.receiver.SmsEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val smsRepository =
        SmsRepository(application)

    private val archivePreferences =
        ArchivePreferences(application)

    private val _conversations =
        MutableStateFlow<List<SmsConversation>>(
            emptyList()
        )

    val conversations: StateFlow<List<SmsConversation>> =
        _conversations.asStateFlow()

    private val _isLoading =
        MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    init {
        observeIncomingSms()
    }

    private fun observeIncomingSms() {
        viewModelScope.launch {
            SmsEventBus.events.collectLatest {
                loadArchivedConversations()
            }
        }
    }

    fun loadArchivedConversations() {
        viewModelScope.launch {

            _isLoading.value = true

            try {
                val result =
                    withContext(Dispatchers.IO) {

                        val archivedThreadIds =
                            archivePreferences
                                .getArchivedThreadIds()

                        smsRepository
                            .getConversations()
                            .filter { conversation ->
                                archivedThreadIds.contains(
                                    conversation.threadId
                                )
                            }
                    }

                _conversations.value = result

            } catch (exception: SecurityException) {

                exception.printStackTrace()
                _conversations.value = emptyList()

            } catch (exception: Exception) {

                exception.printStackTrace()
                _conversations.value = emptyList()

            } finally {
                _isLoading.value = false
            }
        }
    }

    fun unarchiveConversation(
        conversation: SmsConversation
    ) {
        archivePreferences.unarchiveConversation(
            conversation.threadId
        )

        _conversations.value =
            _conversations.value.filterNot {
                it.threadId ==
                        conversation.threadId
            }
    }
}