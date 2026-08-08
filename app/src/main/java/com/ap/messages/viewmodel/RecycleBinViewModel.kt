package com.ap.messages.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ap.messages.data.model.DeletedConversation
import com.ap.messages.data.repository.RecycleBinRepository
import com.ap.messages.data.repository.SmsRepository
import com.ap.messages.sms.DefaultSmsManager
import com.ap.messages.receiver.SmsEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

class RecycleBinViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val recycleBinRepository =
        RecycleBinRepository(application)

    private val smsRepository =
        SmsRepository(application)

    private val defaultSmsManager =
        DefaultSmsManager(application)

    private val _deletedConversations =
        MutableStateFlow<List<DeletedConversation>>(emptyList())

    val deletedConversations: StateFlow<List<DeletedConversation>> =
        _deletedConversations.asStateFlow()

    private val _isLoading = MutableStateFlow(true)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    private val _restoringConversationIds =
        MutableStateFlow<Set<Long>>(emptySet())

    val restoringConversationIds: StateFlow<Set<Long>> =
        _restoringConversationIds.asStateFlow()

    private val _processingConversationIds =
        MutableStateFlow<Set<Long>>(emptySet())

    val processingConversationIds: StateFlow<Set<Long>> =
        _processingConversationIds.asStateFlow()

    init {
        loadDeletedConversations()
    }

    fun loadDeletedConversations() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                _deletedConversations.value =
                    withContext(Dispatchers.IO) {
                        recycleBinRepository
                            .getDeletedConversations()
                            .sortedByDescending { conversation ->
                                conversation.deletedAt
                            }
                    }
            } catch (exception: Exception) {
                exception.printStackTrace()
                _deletedConversations.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreConversation(
        recycleBinId: Long
    ) {
        if (recycleBinId in _processingConversationIds.value) {
            return
        }

        _processingConversationIds.value += recycleBinId
        _restoringConversationIds.value += recycleBinId

        viewModelScope.launch {
            try {
                val restoreCompleted = withContext(Dispatchers.IO) {
                    restoreConversationOnIo(recycleBinId)
                }

                if (restoreCompleted) {
                    SmsEventBus.notifyConversationRestored()
                    loadDeletedConversations()
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            } finally {
                _restoringConversationIds.value -= recycleBinId
                _processingConversationIds.value -= recycleBinId
            }
        }
    }

    fun deleteConversationPermanently(
        recycleBinId: Long
    ) {
        if (recycleBinId in _processingConversationIds.value) {
            return
        }

        _processingConversationIds.value += recycleBinId

        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    recycleBinRepository
                        .deleteSnapshotPermanently(recycleBinId)
                }

                if (deleted) {
                    loadDeletedConversations()
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            } finally {
                _processingConversationIds.value -= recycleBinId
            }
        }
    }

    fun restoreSelected(ids: Set<Long>) {
        if (ids.isEmpty() || ids.any { it in _processingConversationIds.value }) return
        _processingConversationIds.value += ids
        _restoringConversationIds.value += ids
        viewModelScope.launch {
            try {
                val successful = withContext(Dispatchers.IO) { ids.filter { restoreConversationOnIo(it) }.toSet() }
                if (successful.isNotEmpty()) {
                    _deletedConversations.value = _deletedConversations.value.filterNot { it.recycleBinId in successful }
                    SmsEventBus.notifyConversationRestored()
                }
            } catch (e: CancellationException) { throw e }
            finally { _restoringConversationIds.value -= ids; _processingConversationIds.value -= ids }
        }
    }

    fun deleteSelected(ids: Set<Long>) {
        if (ids.isEmpty() || ids.any { it in _processingConversationIds.value }) return
        _processingConversationIds.value += ids
        viewModelScope.launch {
            try {
                val successful = withContext(Dispatchers.IO) { ids.filter { recycleBinRepository.deleteSnapshotPermanently(it) }.toSet() }
                if (successful.isNotEmpty()) _deletedConversations.value = _deletedConversations.value.filterNot { it.recycleBinId in successful }
            } catch (e: CancellationException) { throw e }
            finally { _processingConversationIds.value -= ids }
        }
    }

    private fun restoreConversationOnIo(
        recycleBinId: Long
    ): Boolean {
        if (!defaultSmsManager.isDefaultSmsApp()) {
            return false
        }

        val messages = recycleBinRepository
            .getDeletedMessages(recycleBinId)

        for (message in messages) {
            if (message.restored) {
                continue
            }

            val existingMessage =
                smsRepository.restoredMessageExists(message)
                    ?: return false

            val restored = existingMessage ||
                    smsRepository.restoreMessage(message)

            if (
                !restored ||
                !recycleBinRepository.markMessageRestored(
                    message.localMessageId
                )
            ) {
                return false
            }
        }

        return recycleBinRepository
            .deleteSnapshotIfRestoreComplete(recycleBinId)
    }
}
