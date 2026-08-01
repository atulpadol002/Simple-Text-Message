package com.atul.messageapp.viewmodel

import android.app.Application
import android.telephony.PhoneNumberUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.model.SmsConversation
import com.atul.messageapp.data.preferences.ArchivePreferences
import com.atul.messageapp.data.preferences.BlockedNumbersPreferences
import com.atul.messageapp.data.repository.SmsRepository
import com.atul.messageapp.receiver.SmsEventBus
import com.atul.messageapp.sms.SmsDeleter
import com.atul.messageapp.utils.getContactName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val smsRepository =
        SmsRepository(application)

    private val archivePreferences =
        ArchivePreferences(application)

    private val blockedNumbersPreferences =
        BlockedNumbersPreferences(
            application
        )

    private val smsDeleter =
        SmsDeleter(application)

    private val _conversations =
        MutableStateFlow<List<SmsConversation>>(
            emptyList()
        )

    val conversations:
            StateFlow<List<SmsConversation>> =
        _conversations.asStateFlow()

    private val _isLoading =
        MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    private val _contactNames =
        MutableStateFlow<Map<String, String>>(emptyMap())

    val contactNames: StateFlow<Map<String, String>> =
        _contactNames.asStateFlow()

    private val _deletingConversationIds =
        MutableStateFlow<Set<Long>>(emptySet())

    val deletingConversationIds: StateFlow<Set<Long>> =
        _deletingConversationIds.asStateFlow()

    private var loadJob:
            Job? = null

    private var reloadPending = false

    private val contactNameCache =
        mutableMapOf<String, String>()

    private var stateVersion = 0L

    init {
        observeIncomingSms()
    }

    private fun observeIncomingSms() {

        viewModelScope.launch {

            SmsEventBus.events
                .collectLatest {

                    loadConversations()
                }
        }
    }

    fun loadConversations() {

        if (loadJob?.isActive == true) {
            reloadPending = true
            return
        }

        val showInitialLoading =
            _conversations.value.isEmpty()

        val loadVersion = stateVersion

        if (showInitialLoading) {
            _isLoading.value = true
        }

        loadJob = viewModelScope.launch {

            try {

                val (result, resolvedContactNames) =
                    withContext(
                        Dispatchers.IO
                    ) {

                        val archivedThreadIds =
                            archivePreferences
                                .getArchivedThreadIds()

                        val conversations = smsRepository
                            .getConversations()
                            .filterNot {
                                    conversation ->

                                val isArchived =
                                    archivedThreadIds
                                        .contains(
                                            conversation
                                                .threadId
                                        )

                                val isBlocked =
                                    blockedNumbersPreferences
                                        .isNumberBlocked(
                                            conversation
                                                .address
                                        )

                                isArchived ||
                                        isBlocked
                            }

                        val names = conversations
                            .map { conversation ->
                                normalizeAddress(conversation.address)
                            }
                            .distinct()
                            .associateWith { normalizedAddress ->
                                contactNameCache.getOrPut(normalizedAddress) {
                                    val address = conversations
                                        .first { conversation ->
                                            normalizeAddress(conversation.address) ==
                                                    normalizedAddress
                                        }
                                        .address

                                    getContactName(
                                        context = getApplication(),
                                        phoneNumber = address
                                    )
                                }
                            }

                        conversations to names
                    }

                if (loadVersion == stateVersion) {
                    _conversations.value = result
                    _contactNames.value = resolvedContactNames
                } else {
                    reloadPending = true
                }

            } catch (
                exception: SecurityException
            ) {

                exception.printStackTrace()

                if (_conversations.value.isEmpty()) {
                    _conversations.value = emptyList()
                }

            } catch (
                exception: Exception
            ) {

                exception.printStackTrace()

                if (_conversations.value.isEmpty()) {
                    _conversations.value = emptyList()
                }

            } finally {
                if (showInitialLoading) {
                    _isLoading.value = false
                }

                loadJob = null

                if (reloadPending) {
                    reloadPending = false
                    loadConversations()
                }
            }
        }
    }

    fun deleteConversation(
        conversation: SmsConversation
    ) {
        val threadId = conversation.threadId

        if (threadId in _deletingConversationIds.value) {
            return
        }

        _deletingConversationIds.value += threadId

        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    smsDeleter.deleteConversation(threadId)
                }

                if (deleted) {
                    stateVersion++
                    _conversations.value =
                        _conversations.value.filterNot {
                            it.threadId == threadId
                        }
                }
            } finally {
                _deletingConversationIds.value -= threadId
            }
        }
    }

    companion object {
        fun normalizeAddress(address: String): String =
            PhoneNumberUtils.normalizeNumber(address)
                .ifBlank { address.trim() }
    }

    fun archiveConversation(
        conversation: SmsConversation
    ) {

        archivePreferences
            .archiveConversation(
                conversation.threadId
            )

        _conversations.value =
            _conversations.value
                .filterNot {

                    it.threadId ==
                            conversation.threadId
                }
    }
}
