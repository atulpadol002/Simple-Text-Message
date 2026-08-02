package com.atul.messageapp.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.model.Message
import com.atul.messageapp.data.model.MessageStatus
import com.atul.messageapp.data.model.ScheduledSms
import com.atul.messageapp.data.preferences.ScheduledSmsPreferences
import com.atul.messageapp.data.preferences.StarredMessagesPreferences
import com.atul.messageapp.data.repository.MessageRepository
import com.atul.messageapp.sms.ScheduledSmsScheduler
import com.atul.messageapp.sms.SmsDeleter
import com.atul.messageapp.utils.getContactName
import com.atul.messageapp.utils.ContactPresentationResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val repository =
        MessageRepository(appContext)

    private val starredMessagesPreferences =
        StarredMessagesPreferences(
            appContext
        )

    private val scheduledSmsPreferences =
        ScheduledSmsPreferences(
            appContext
        )

    private val scheduledSmsScheduler =
        ScheduledSmsScheduler(
            appContext
        )

    private val smsDeleter = SmsDeleter(appContext)
    private val contactPresentationResolver = ContactPresentationResolver(appContext)

    data class ContactAvatarState(val displayName: String = "", val photo: Bitmap? = null)
    private val _contactAvatar = MutableStateFlow(ContactAvatarState())
    val contactAvatar: StateFlow<ContactAvatarState> = _contactAvatar.asStateFlow()
    private val _isDeletingConversation = MutableStateFlow(false)
    val isDeletingConversation: StateFlow<Boolean> = _isDeletingConversation.asStateFlow()

    private val _messages =
        MutableStateFlow<List<Message>>(
            emptyList()
        )

    val messages: StateFlow<List<Message>> =
        _messages.asStateFlow()

    private val _starredMessageIds =
        MutableStateFlow<Set<Long>>(
            emptySet()
        )

    val starredMessageIds:
            StateFlow<Set<Long>> =
        _starredMessageIds.asStateFlow()

    private val _scheduledMessages =
        MutableStateFlow<List<ScheduledSms>>(
            emptyList()
        )

    val scheduledMessages:
            StateFlow<List<ScheduledSms>> =
        _scheduledMessages.asStateFlow()

    private var currentConversationId:
            Long? = null

    private var currentPhoneNumber:
            String? = null

    private var refreshJob:
            Job? = null

    private var pendingThreadIdMessageId:
            Long? = null
    private val pendingTerminalStatuses = mutableMapOf<Long, MessageStatus>()

    private val smsContentObserver =
        object : ContentObserver(
            Handler(
                Looper.getMainLooper()
            )
        ) {

            override fun onChange(
                selfChange: Boolean
            ) {

                super.onChange(
                    selfChange
                )

                scheduleProviderRefresh()
            }

            override fun onChange(
                selfChange: Boolean,
                uri: Uri?
            ) {

                scheduleProviderRefresh()
            }
        }

    init {

        loadStarredMessageIds()

        try {

            appContext.contentResolver
                .registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    smsContentObserver
                )

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()
        }
    }

    fun loadMessages(
        conversationId: Long,
        phoneNumber: String
    ) {

        if (
            conversationId > 0L ||
            currentConversationId == null ||
            currentConversationId == 0L
        ) {
            currentConversationId =
                conversationId
        }

        currentPhoneNumber =
            phoneNumber

        loadContactAvatar(phoneNumber)

        loadScheduledMessages(
            phoneNumber
        )

        if (conversationId <= 0L) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.markThreadAsRead(
                conversationId
            )
        }

        viewModelScope.launch {

            val result =
                withContext(
                    Dispatchers.IO
                ) {

                    repository.getMessages(
                        conversationId
                    )
                }

            if (
                currentConversationId ==
                conversationId
            ) {
                _messages.value = mergeProviderMessages(result)
            }
        }
    }

    fun refreshMessages() {

        val conversationId =
            currentConversationId
                ?: return

        if (conversationId <= 0L) {
            return
        }

        loadStarredMessageIds()

        currentPhoneNumber?.let {
                phoneNumber ->

            loadScheduledMessages(
                phoneNumber
            )
        }

        viewModelScope.launch {

            val result =
                withContext(
                    Dispatchers.IO
                ) {

                    repository.markThreadAsRead(
                        conversationId
                    )

                    repository.getMessages(
                        conversationId
                    )
                }

            if (
                currentConversationId ==
                conversationId
            ) {
                _messages.value = mergeProviderMessages(result)
            }
        }
    }

    fun loadScheduledMessages(
        phoneNumber: String
    ) {

        currentPhoneNumber =
            phoneNumber

        _scheduledMessages.value =
            scheduledSmsPreferences
                .getScheduledMessagesForNumber(
                    phoneNumber
                )
                .filter {
                        scheduledSms ->

                    scheduledSms.scheduledTime >
                            System.currentTimeMillis()
                }
                .sortedBy {
                        scheduledSms ->

                    scheduledSms.scheduledTime
                }
    }

    fun scheduleMessage(
        contactName: String,
        phoneNumber: String,
        message: String,
        scheduledTime: Long
    ): Boolean {

        val cleanPhoneNumber =
            phoneNumber.trim()

        val cleanMessage =
            message.trim()

        if (
            cleanPhoneNumber.isBlank() ||
            cleanMessage.isBlank() ||
            scheduledTime <=
            System.currentTimeMillis()
        ) {
            return false
        }

        val scheduledSms =
            ScheduledSms(
                id = createUniqueId(),
                contactName =
                    contactName.trim(),
                phoneNumber =
                    cleanPhoneNumber,
                message =
                    cleanMessage,
                scheduledTime =
                    scheduledTime
            )

        val persisted =
            scheduledSmsPreferences
                .saveScheduledMessage(
                    scheduledSms
                )

        if (!persisted) {
            return false
        }

        val scheduled =
            scheduledSmsScheduler.schedule(
                scheduledSms
            )

        if (!scheduled) {

            scheduledSmsPreferences
                .deleteScheduledMessage(
                    scheduledSms.id
                )

            return false
        }

        loadScheduledMessages(
            cleanPhoneNumber
        )

        return true
    }

    /*
     * Edit button click hote hi message editing mode me
     * mark hoga aur original alarm cancel hoga.
     */
    fun beginEditingScheduledMessage(
        scheduledSms: ScheduledSms
    ): Boolean {

        val savedMessage =
            scheduledSmsPreferences
                .getScheduledMessage(
                    scheduledSms.id
                )
                ?: return false

        if (
            savedMessage.scheduledTime <=
            System.currentTimeMillis()
        ) {

            loadScheduledMessages(
                savedMessage.phoneNumber
            )

            return false
        }

        val marked =
            scheduledSmsPreferences
                .markMessageEditing(
                    savedMessage.id
                )

        if (!marked) {
            return false
        }

        scheduledSmsScheduler.cancel(
            savedMessage.id
        )

        return true
    }

    /*
     * User edit dialog close/cancel kare to:
     * original time future me hai -> alarm restore
     * original time pass ho gaya -> schedule remove
     */
    fun cancelEditingScheduledMessage(
        scheduledSms: ScheduledSms
    ) {

        scheduledSmsPreferences
            .clearMessageEditing(
                scheduledSms.id
            )

        if (
            scheduledSms.scheduledTime >
            System.currentTimeMillis()
        ) {

            val restored =
                scheduledSmsScheduler.schedule(
                    scheduledSms
                )

            if (!restored) {

                scheduledSmsPreferences
                    .deleteScheduledMessage(
                        scheduledSms.id
                    )
            }

        } else {

            scheduledSmsPreferences
                .deleteScheduledMessage(
                    scheduledSms.id
                )
        }

        loadScheduledMessages(
            scheduledSms.phoneNumber
        )
    }

    fun updateScheduledMessage(
        oldScheduledSms: ScheduledSms,
        message: String,
        scheduledTime: Long
    ): Boolean {

        val cleanMessage =
            message.trim()

        if (
            cleanMessage.isBlank() ||
            scheduledTime <=
            System.currentTimeMillis()
        ) {
            return false
        }

        val savedOldMessage =
            scheduledSmsPreferences
                .getScheduledMessage(
                    oldScheduledSms.id
                )
                ?: return false

        val updatedScheduledSms =
            savedOldMessage.copy(
                message =
                    cleanMessage,
                scheduledTime =
                    scheduledTime
            )

        /*
         * beginEditingScheduledMessage() alarm pehle hi
         * cancel karta hai, phir bhi safety ke liye cancel.
         */
        scheduledSmsScheduler.cancel(
            savedOldMessage.id
        )

        val persisted =
            scheduledSmsPreferences
                .saveScheduledMessage(
                    updatedScheduledSms
                )

        val rescheduled =
            persisted &&
                    scheduledSmsScheduler.schedule(
                        updatedScheduledSms
                    )

        if (!rescheduled) {

            val oldMessageRestored =
                scheduledSmsPreferences
                    .saveScheduledMessage(
                        savedOldMessage
                    )

            if (!oldMessageRestored) {

                val updatedMessageRestored =
                    scheduledSmsPreferences
                        .saveScheduledMessage(
                            updatedScheduledSms
                        )

                val updatedAlarmRestored =
                    updatedMessageRestored &&
                            scheduledSmsScheduler.schedule(
                                updatedScheduledSms
                            )

                scheduledSmsPreferences
                    .clearMessageEditing(
                        updatedScheduledSms.id
                    )

                loadScheduledMessages(
                    updatedScheduledSms.phoneNumber
                )

                return updatedAlarmRestored
            }

            scheduledSmsPreferences
                .clearMessageEditing(
                    savedOldMessage.id
                )

            if (
                savedOldMessage.scheduledTime >
                System.currentTimeMillis()
            ) {

                scheduledSmsScheduler.schedule(
                    savedOldMessage
                )
            }

            loadScheduledMessages(
                savedOldMessage.phoneNumber
            )

            return false
        }

        scheduledSmsPreferences
            .clearMessageEditing(
                updatedScheduledSms.id
            )

        loadScheduledMessages(
            updatedScheduledSms.phoneNumber
        )

        return true
    }

    fun cancelScheduledMessage(
        scheduledSms: ScheduledSms
    ) {

        scheduledSmsScheduler.cancel(
            scheduledSms.id
        )

        scheduledSmsPreferences
            .clearMessageEditing(
                scheduledSms.id
            )

        scheduledSmsPreferences
            .deleteScheduledMessage(
                scheduledSms.id
            )

        loadScheduledMessages(
            scheduledSms.phoneNumber
        )
    }

    fun sendScheduledMessageNow(
        scheduledSms: ScheduledSms
    ): Boolean {

        if (
            scheduledSms.phoneNumber.isBlank() ||
            scheduledSms.message.isBlank()
        ) {
            return false
        }

        scheduledSmsScheduler.cancel(
            scheduledSms.id
        )

        scheduledSmsPreferences
            .clearMessageEditing(
                scheduledSms.id
            )

        scheduledSmsPreferences
            .deleteScheduledMessage(
                scheduledSms.id
            )

        loadScheduledMessages(
            scheduledSms.phoneNumber
        )

        viewModelScope.launch {

            val insertedMessageId =
                withContext(
                    Dispatchers.IO
                ) {

                    repository
                        .insertOutgoingMessage(
                            phoneNumber =
                                scheduledSms
                                    .phoneNumber,
                            body =
                                scheduledSms
                                    .message
                        )
                }

            resolveCurrentConversationId(
                insertedMessageId
            )

            val handedOff =
                repository.sendSms(
                    phoneNumber =
                        scheduledSms.phoneNumber,
                    message =
                        scheduledSms.message,
                    onSentResult = {
                            confirmedSent ->

                        if (
                            insertedMessageId !=
                            -1L
                        ) {

                            viewModelScope.launch(
                                Dispatchers.IO
                            ) {

                                if (confirmedSent) {

                                    repository
                                        .markMessageSent(
                                            insertedMessageId
                                        )

                                } else {

                                    repository
                                        .markMessageFailed(
                                            insertedMessageId
                                        )
                                }
                            }
                        }

                        refreshMessages()
                    }
                )

            if (!handedOff) {

                if (
                    insertedMessageId !=
                    -1L
                ) {

                    withContext(
                        Dispatchers.IO
                    ) {

                        repository
                            .markMessageFailed(
                                insertedMessageId
                            )
                    }
                }

                refreshMessages()
            }
        }

        return true
    }

    fun isMessageStarred(
        messageId: Long
    ): Boolean {

        return _starredMessageIds
            .value
            .contains(
                messageId
            )
    }

    fun toggleStarMessage(
        message: Message
    ): Boolean {

        if (
            !isPersistedId(
                message.id
            )
        ) {
            return false
        }

        val currentlyStarred =
            isMessageStarred(
                message.id
            )

        val changed =
            if (currentlyStarred) {

                starredMessagesPreferences
                    .unstarMessage(
                        message.id
                    )

            } else {

                starredMessagesPreferences
                    .starMessage(
                        message.id
                    )
            }

        if (changed) {

            loadStarredMessageIds()
        }

        return changed
    }

    private fun loadContactAvatar(phoneNumber: String) {
        viewModelScope.launch {
            _contactAvatar.value = withContext(Dispatchers.IO) {
                contactPresentationResolver.resolve(phoneNumber).let {
                    ContactAvatarState(it.displayName, it.photo)
                }
            }
        }
    }

    fun deleteConversation(threadId: Long, onComplete: (Boolean) -> Unit) {
        if (threadId <= 0L || _isDeletingConversation.value) return
        _isDeletingConversation.value = true
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { smsDeleter.deleteConversation(threadId) }
            _isDeletingConversation.value = false
            onComplete(deleted)
        }
    }

    fun setMessagesStarred(
        messages: List<Message>,
        starred: Boolean
    ): Boolean {
        val persistedMessages = messages.filter { isPersistedId(it.id) }
        if (persistedMessages.isEmpty()) return false

        var changed = false
        persistedMessages.forEach { message ->
            val messageChanged = if (starred) {
                starredMessagesPreferences.starMessage(message.id)
            } else {
                starredMessagesPreferences.unstarMessage(message.id)
            }
            changed = messageChanged || changed
        }
        loadStarredMessageIds()
        return changed
    }

    fun deleteMessages(
        messages: List<Message>,
        onComplete: (Boolean) -> Unit
    ) {
        val persistedMessages = messages.filter { isPersistedId(it.id) }
        if (persistedMessages.isEmpty()) {
            onComplete(false)
            return
        }

        viewModelScope.launch {
            val deletedIds = withContext(Dispatchers.IO) {
                persistedMessages.mapNotNull { message ->
                    if (repository.deleteMessage(message.id)) message.id else null
                }.toSet()
            }

            if (deletedIds.isNotEmpty()) {
                _messages.value = _messages.value.filterNot { it.id in deletedIds }
                deletedIds.forEach { starredMessagesPreferences.unstarMessage(it) }
                loadStarredMessageIds()
            }
            onComplete(deletedIds.size == persistedMessages.size)
        }
    }

    fun sendMessage(
        phoneNumber: String,
        conversationId: Long,
        message: String
    ) {

        if (
            conversationId > 0L ||
            currentConversationId == null ||
            currentConversationId == 0L
        ) {
            currentConversationId =
                conversationId
        }

        currentPhoneNumber =
            phoneNumber

        viewModelScope.launch {

            val insertedId =
                withContext(
                    Dispatchers.IO
                ) {

                    repository
                        .insertOutgoingMessage(
                            phoneNumber =
                                phoneNumber,
                            body =
                                message
                        )
                }

            resolveCurrentConversationId(
                insertedId
            )

            val messageId =
                if (
                    insertedId != -1L
                ) {

                    insertedId

                } else {

                    -System.nanoTime()
                }

            val optimisticMessage =
                Message(
                    id =
                        messageId,
                    conversationId =
                        currentConversationId
                            ?: conversationId,
                    phoneNumber =
                        phoneNumber,
                    body =
                        message,
                    timestamp =
                        System.currentTimeMillis(),
                    isIncoming =
                        false,
                    isRead =
                        true,
                    status =
                        MessageStatus.SENDING
                )

            _messages.value =
                _messages.value +
                        optimisticMessage

            dispatchSend(
                messageId =
                    messageId,
                phoneNumber =
                    phoneNumber,
                body =
                    message
            )
        }
    }

    fun retrySend(
        failedMessage: Message
    ) {

        updateMessageStatus(
            messageId =
                failedMessage.id,
            newStatus =
                MessageStatus.SENDING
        )

        if (
            isPersistedId(
                failedMessage.id
            )
        ) {

            viewModelScope.launch {

                withContext(
                    Dispatchers.IO
                ) {

                    repository
                        .markMessageSending(
                            failedMessage.id
                        )
                }
            }
        }

        dispatchSend(
            messageId =
                failedMessage.id,
            phoneNumber =
                failedMessage.phoneNumber,
            body =
                failedMessage.body
        )
    }

    fun deleteMessage(
        message: Message
    ) {

        _messages.value =
            _messages.value
                .filterNot {
                        existingMessage ->

                    existingMessage.id ==
                            message.id
                }

        if (
            !isPersistedId(
                message.id
            )
        ) {
            return
        }

        viewModelScope.launch {

            val deleted =
                withContext(
                    Dispatchers.IO
                ) {

                    repository.deleteMessage(
                        messageId =
                            message.id
                    )
                }

            if (deleted) {

                if (
                    starredMessagesPreferences
                        .isMessageStarred(
                            message.id
                        )
                ) {

                    starredMessagesPreferences
                        .unstarMessage(
                            message.id
                        )

                    loadStarredMessageIds()
                }

            } else {

                val conversationId =
                    currentConversationId
                        ?: return@launch

                val refreshedMessages =
                    withContext(
                        Dispatchers.IO
                    ) {

                        repository.getMessages(
                            conversationId =
                                conversationId
                        )
                    }

                if (
                    currentConversationId ==
                    conversationId
                ) {
                    _messages.value = mergeProviderMessages(refreshedMessages)
                }
            }
        }
    }

    private fun loadStarredMessageIds() {

        _starredMessageIds.value =
            starredMessagesPreferences
                .getStarredMessageIds()
    }

    private fun dispatchSend(
        messageId: Long,
        phoneNumber: String,
        body: String
    ) {

        val handedOff =
            repository.sendSms(
                phoneNumber =
                    phoneNumber,
                message =
                    body,
                onSentResult = {
                        confirmedSent ->

                    onSendResult(
                        messageId =
                            messageId,
                        confirmedSent =
                            confirmedSent
                    )
                }
            )

        if (!handedOff) {

            onSendResult(
                messageId =
                    messageId,
                confirmedSent =
                    false
            )
        }
    }

    private fun onSendResult(
        messageId: Long,
        confirmedSent: Boolean
    ) {

        updateMessageStatus(
            messageId =
                messageId,
            newStatus =
                if (confirmedSent) {

                    MessageStatus.SENT

                } else {

                    MessageStatus.FAILED
                }
        )

        if (isPersistedId(messageId)) {
            pendingTerminalStatuses[messageId] = if (confirmedSent) MessageStatus.SENT else MessageStatus.FAILED
        }

        if (
            isPersistedId(
                messageId
            )
        ) {

            viewModelScope.launch {

                val updated = withContext(
                    Dispatchers.IO
                ) {

                    if (confirmedSent) {

                        repository
                            .markMessageSent(
                                messageId
                            )

                    } else {

                        repository
                            .markMessageFailed(
                                messageId
                            )
                    }
                }
                if (updated) {
                    val persisted = withContext(Dispatchers.IO) { repository.getMessage(messageId) }
                    if (persisted != null) {
                        _messages.value = _messages.value.map { if (it.id == messageId) persisted else it }
                        if (persisted.status == pendingTerminalStatuses[messageId]) {
                            pendingTerminalStatuses.remove(messageId)
                        }
                    }
                }
            }
        }
    }

    private fun scheduleProviderRefresh() {

        refreshJob?.cancel()

        refreshJob =
            viewModelScope.launch {

                delay(
                    250L
                )

                retryPendingThreadIdResolution()

                val conversationId =
                    currentConversationId
                        ?: return@launch

                if (conversationId <= 0L) {
                    return@launch
                }

                val refreshedMessages =
                    withContext(
                        Dispatchers.IO
                    ) {

                        repository.getMessages(
                            conversationId
                        )
                    }

                if (
                    currentConversationId ==
                    conversationId
                ) {
                    _messages.value = mergeProviderMessages(refreshedMessages)
                }

                currentPhoneNumber
                    ?.let {
                            phoneNumber ->

                        loadScheduledMessages(
                            phoneNumber
                        )
                    }
            }
    }

    private fun mergeProviderMessages(providerMessages: List<Message>): List<Message> {
        if (pendingTerminalStatuses.isEmpty()) return providerMessages
        return providerMessages.map { message ->
            pendingTerminalStatuses[message.id]?.let { message.copy(status = it) } ?: message
        }
    }

    private suspend fun resolveCurrentConversationId(
        insertedMessageId: Long
    ) {

        if (
            insertedMessageId <= 0L ||
            currentConversationId != 0L
        ) {
            return
        }

        val resolvedThreadId =
            withContext(
                Dispatchers.IO
            ) {
                repository.getThreadIdForMessage(
                    insertedMessageId
                )
            }

        if (
            currentConversationId == 0L &&
            resolvedThreadId > 0L
        ) {
            currentConversationId =
                resolvedThreadId

            pendingThreadIdMessageId =
                null

        } else if (
            currentConversationId == 0L
        ) {
            pendingThreadIdMessageId =
                insertedMessageId
        }
    }

    private suspend fun retryPendingThreadIdResolution() {

        val pendingMessageId =
            pendingThreadIdMessageId
                ?: return

        if (currentConversationId != 0L) {
            pendingThreadIdMessageId =
                null
            return
        }

        val resolvedThreadId =
            withContext(
                Dispatchers.IO
            ) {
                repository.getThreadIdForMessage(
                    pendingMessageId
                )
            }

        if (
            currentConversationId == 0L &&
            resolvedThreadId > 0L
        ) {
            currentConversationId =
                resolvedThreadId

            pendingThreadIdMessageId =
                null
        }
    }

    private fun updateMessageStatus(
        messageId: Long,
        newStatus: MessageStatus
    ) {

        _messages.value =
            _messages.value.map {
                    existing ->

                if (
                    existing.id ==
                    messageId
                ) {

                    existing.copy(
                        status =
                            newStatus
                    )

                } else {

                    existing
                }
            }
    }

    private fun createUniqueId():
            Long {

        return System.currentTimeMillis() +
                (0..999).random()
    }

    private fun isPersistedId(
        id: Long
    ): Boolean {

        return id >= 0
    }

    override fun onCleared() {

        refreshJob?.cancel()

        try {

            appContext.contentResolver
                .unregisterContentObserver(
                    smsContentObserver
                )

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()
        }

        super.onCleared()
    }
}
