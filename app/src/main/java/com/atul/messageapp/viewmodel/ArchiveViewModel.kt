package com.atul.messageapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.model.SmsConversation
import com.atul.messageapp.data.preferences.ArchivePreferences
import com.atul.messageapp.data.preferences.PinnedConversationsPreferences
import com.atul.messageapp.data.repository.SmsRepository
import com.atul.messageapp.receiver.SmsEventBus
import com.atul.messageapp.utils.getContactName
import com.atul.messageapp.utils.ContactPresentation
import com.atul.messageapp.utils.ContactPresentationResolver
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

    private data class ArchiveContent(
        val conversations: List<SmsConversation>,
        val names: Map<Long, String>,
        val pinnedIds: Set<Long>,
        val presentations: Map<Long, ContactPresentation>
    )

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
    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private val _selectedThreadIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedThreadIds: StateFlow<Set<Long>> = _selectedThreadIds.asStateFlow()

    private val _contactNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val contactNames: StateFlow<Map<Long, String>> = _contactNames.asStateFlow()

    private val pinnedPreferences = PinnedConversationsPreferences(application)
    private val _pinnedThreadIds = MutableStateFlow<Set<Long>>(emptySet())
    val pinnedThreadIds: StateFlow<Set<Long>> = _pinnedThreadIds.asStateFlow()
    private val _contactPresentations = MutableStateFlow<Map<Long, ContactPresentation>>(emptyMap())
    val contactPresentations: StateFlow<Map<Long, ContactPresentation>> = _contactPresentations.asStateFlow()
    private val contactResolver = ContactPresentationResolver(application)

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

                        val pinnedIds = pinnedPreferences.getPinnedThreadIds()
                        val archivedConversations = providerConversations
                            .filter { it.threadId in archivedThreadIds }
                            .sortedWith(
                                compareByDescending<SmsConversation> { it.threadId in pinnedIds }
                                    .thenByDescending { it.date }
                            )
                        val presentations = archivedConversations.associate {
                            it.threadId to contactResolver.resolve(it.address)
                        }

                        Triple(
                            archivedThreadIds,
                            validProviderThreadIds,
                            ArchiveContent(archivedConversations, presentations.mapValues { it.value.displayName }, pinnedIds intersect validProviderThreadIds, presentations)
                        )
                    }

                if (loadJob === currentLoadJob) {

                    archivePreferences
                        .removeArchivedThreadIds(
                            result.first -
                                    result.second
                        )

                    _conversations.value = result.third.conversations
                    _contactNames.value = result.third.names
                    _pinnedThreadIds.value = result.third.pinnedIds
                    _contactPresentations.value = result.third.presentations
                    _hasLoaded.value = true
                }

            } catch (
                exception: CancellationException
            ) {

                throw exception

            } catch (exception: SecurityException) {

                exception.printStackTrace()

                if (loadJob === currentLoadJob) {

                    if (_conversations.value.isEmpty()) _conversations.value = emptyList()
                }

            } catch (exception: Exception) {

                exception.printStackTrace()

                if (loadJob === currentLoadJob) {

                    if (_conversations.value.isEmpty()) _conversations.value = emptyList()
                }

            } finally {

                if (loadJob === currentLoadJob) {

                    _isLoading.value = false
                    _hasLoaded.value = true
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
