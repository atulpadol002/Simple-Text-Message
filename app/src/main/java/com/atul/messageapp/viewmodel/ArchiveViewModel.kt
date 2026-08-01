package com.atul.messageapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.model.SmsConversation
import com.atul.messageapp.data.preferences.ArchivePreferences
import com.atul.messageapp.data.repository.SmsRepository
import com.atul.messageapp.receiver.SmsEventBus
import com.atul.messageapp.utils.getContactName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val _selectedThreadIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedThreadIds: StateFlow<Set<Long>> = _selectedThreadIds.asStateFlow()

    private val _contactNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val contactNames: StateFlow<Map<Long, String>> = _contactNames.asStateFlow()

    private var loadJob:
            Job? = null

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

        loadJob?.cancel()

        val newLoadJob =
            viewModelScope.launch(
                start = CoroutineStart.LAZY
            ) {

            val currentLoadJob =
                coroutineContext[Job]

            _isLoading.value = true

            try {
                val result =
                    withContext(Dispatchers.IO) {

                        val archivedThreadIds =
                            archivePreferences
                                .getArchivedThreadIds()

                        val providerConversations =
                            smsRepository
                                .getConversations()

                        val validProviderThreadIds =
                            providerConversations
                                .map { conversation ->
                                    conversation.threadId
                                }
                                .toSet()

                        val archivedConversations = providerConversations
                            .filter { it.threadId in archivedThreadIds }

                        Triple(
                            archivedThreadIds,
                            validProviderThreadIds,
                            archivedConversations to archivedConversations.associate {
                                it.threadId to getContactName(getApplication(), it.address)
                            }
                        )
                    }

                if (loadJob === currentLoadJob) {

                    archivePreferences
                        .removeArchivedThreadIds(
                            result.first -
                                    result.second
                        )

                    _conversations.value = result.third.first
                    _contactNames.value = result.third.second
                }

            } catch (
                exception: CancellationException
            ) {

                throw exception

            } catch (exception: SecurityException) {

                exception.printStackTrace()

                if (loadJob === currentLoadJob) {

                    _conversations.value = emptyList()
                }

            } catch (exception: Exception) {

                exception.printStackTrace()

                if (loadJob === currentLoadJob) {

                    _conversations.value = emptyList()
                }

            } finally {

                if (loadJob === currentLoadJob) {

                    _isLoading.value = false
                }
            }
        }

        loadJob = newLoadJob
        newLoadJob.start()
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

    fun toggleSelection(threadId: Long) {
        _selectedThreadIds.value = _selectedThreadIds.value.toMutableSet().apply {
            if (!add(threadId)) remove(threadId)
        }
    }

    fun clearSelection() {
        _selectedThreadIds.value = emptySet()
    }

    fun unarchiveSelected() {
        val selected = _selectedThreadIds.value
        if (selected.isEmpty()) return
        selected.forEach(archivePreferences::unarchiveConversation)
        _conversations.value = _conversations.value.filterNot { it.threadId in selected }
        clearSelection()
    }
}
