package com.atul.messageapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.model.ScheduledSms
import com.atul.messageapp.data.preferences.ScheduledSmsPreferences
import com.atul.messageapp.data.repository.MessageRepository
import com.atul.messageapp.sms.ScheduledSmsScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduledSmsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val preferences =
        ScheduledSmsPreferences(appContext)

    private val scheduler =
        ScheduledSmsScheduler(appContext)

    private val messageRepository =
        MessageRepository(appContext)

    private val _scheduledMessages =
        MutableStateFlow<List<ScheduledSms>>(
            emptyList()
        )

    val scheduledMessages:
            StateFlow<List<ScheduledSms>> =
        _scheduledMessages.asStateFlow()

    init {
        loadScheduledMessages()
    }

    fun loadScheduledMessages() {

        val currentTime =
            System.currentTimeMillis()

        _scheduledMessages.value =
            preferences
                .getScheduledMessages()
                .filter { scheduledSms ->

                    scheduledSms.scheduledTime >
                            currentTime
                }
                .sortedBy { scheduledSms ->

                    scheduledSms.scheduledTime
                }
    }

    fun scheduleMessage(
        contactName: String = "",
        phoneNumber: String,
        message: String,
        scheduledTime: Long
    ): Boolean {

        val cleanContactName =
            contactName.trim()

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
                    cleanContactName,
                phoneNumber =
                    cleanPhoneNumber,
                message =
                    cleanMessage,
                scheduledTime =
                    scheduledTime
            )

        val persisted =
            preferences.saveScheduledMessage(
                scheduledSms
            )

        if (!persisted) {
            return false
        }

        val scheduled =
            scheduler.schedule(
                scheduledSms
            )

        if (!scheduled) {

            preferences.deleteScheduledMessage(
                scheduledSms.id
            )

            return false
        }

        loadScheduledMessages()

        return true
    }

    fun beginEditingScheduledMessage(
        scheduledSms: ScheduledSms
    ): Boolean {

        val savedMessage =
            preferences.getScheduledMessage(
                scheduledSms.id
            ) ?: return false

        if (
            savedMessage.scheduledTime <=
            System.currentTimeMillis()
        ) {

            loadScheduledMessages()
            return false
        }

        val marked =
            preferences.markMessageEditing(
                savedMessage.id
            )

        if (!marked) {
            return false
        }

        scheduler.cancel(
            savedMessage.id
        )

        return true
    }

    fun cancelEditingScheduledMessage(
        scheduledSms: ScheduledSms
    ) {

        preferences.clearMessageEditing(
            scheduledSms.id
        )

        if (
            scheduledSms.scheduledTime >
            System.currentTimeMillis()
        ) {

            val restored =
                scheduler.schedule(
                    scheduledSms
                )

            if (!restored) {

                preferences.deleteScheduledMessage(
                    scheduledSms.id
                )
            }

        } else {

            preferences.deleteScheduledMessage(
                scheduledSms.id
            )
        }

        loadScheduledMessages()
    }

    fun updateScheduledMessage(
        oldScheduledSms: ScheduledSms,
        contactName: String,
        phoneNumber: String,
        message: String,
        scheduledTime: Long
    ): Boolean {

        val cleanContactName =
            contactName.trim()

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

        val savedOldMessage =
            preferences.getScheduledMessage(
                oldScheduledSms.id
            ) ?: return false

        val updatedScheduledSms =
            savedOldMessage.copy(
                contactName =
                    cleanContactName,
                phoneNumber =
                    cleanPhoneNumber,
                message =
                    cleanMessage,
                scheduledTime =
                    scheduledTime
            )

        scheduler.cancel(
            savedOldMessage.id
        )

        val persisted =
            preferences.saveScheduledMessage(
                updatedScheduledSms
            )

        val scheduled =
            persisted &&
                    scheduler.schedule(
                        updatedScheduledSms
                    )

        if (!scheduled) {

            val oldMessageRestored =
                preferences.saveScheduledMessage(
                    savedOldMessage
                )

            if (!oldMessageRestored) {

                val updatedMessageRestored =
                    preferences.saveScheduledMessage(
                        updatedScheduledSms
                    )

                val updatedAlarmRestored =
                    updatedMessageRestored &&
                            scheduler.schedule(
                                updatedScheduledSms
                            )

                preferences.clearMessageEditing(
                    updatedScheduledSms.id
                )

                loadScheduledMessages()

                return updatedAlarmRestored
            }

            preferences.clearMessageEditing(
                savedOldMessage.id
            )

            if (
                savedOldMessage.scheduledTime >
                System.currentTimeMillis()
            ) {

                scheduler.schedule(
                    savedOldMessage
                )
            }

            loadScheduledMessages()

            return false
        }

        preferences.clearMessageEditing(
            updatedScheduledSms.id
        )

        loadScheduledMessages()

        return true
    }

    fun sendNow(
        scheduledSms: ScheduledSms
    ): Boolean {

        if (
            scheduledSms.phoneNumber.isBlank() ||
            scheduledSms.message.isBlank()
        ) {
            return false
        }

        scheduler.cancel(
            scheduledSms.id
        )

        preferences.clearMessageEditing(
            scheduledSms.id
        )

        preferences.deleteScheduledMessage(
            scheduledSms.id
        )

        loadScheduledMessages()

        viewModelScope.launch {

            val insertedMessageId =
                withContext(Dispatchers.IO) {

                    messageRepository
                        .insertOutgoingMessage(
                            phoneNumber =
                                scheduledSms.phoneNumber,
                            body =
                                scheduledSms.message
                        )
                }

            val handedOff =
                messageRepository.sendSms(
                    phoneNumber =
                        scheduledSms.phoneNumber,
                    message =
                        scheduledSms.message,
                    onSentResult = { sent ->

                        viewModelScope.launch(
                            Dispatchers.IO
                        ) {

                            if (
                                insertedMessageId != -1L
                            ) {

                                if (sent) {

                                    messageRepository
                                        .markMessageSent(
                                            insertedMessageId
                                        )

                                } else {

                                    messageRepository
                                        .markMessageFailed(
                                            insertedMessageId
                                        )
                                }
                            }
                        }
                    }
                )

            if (!handedOff) {

                if (
                    insertedMessageId != -1L
                ) {

                    withContext(Dispatchers.IO) {

                        messageRepository
                            .markMessageFailed(
                                insertedMessageId
                            )
                    }
                }
            }
        }

        return true
    }

    fun cancelScheduledMessage(
        scheduledSms: ScheduledSms
    ) {

        scheduler.cancel(
            scheduledSms.id
        )

        preferences.clearMessageEditing(
            scheduledSms.id
        )

        preferences.deleteScheduledMessage(
            scheduledSms.id
        )

        loadScheduledMessages()
    }

    fun getScheduledMessagesForNumber(
        phoneNumber: String
    ): List<ScheduledSms> {

        return preferences
            .getScheduledMessagesForNumber(
                phoneNumber
            )
            .filter { scheduledSms ->

                scheduledSms.scheduledTime >
                        System.currentTimeMillis()
            }
            .sortedBy { scheduledSms ->

                scheduledSms.scheduledTime
            }
    }

    private fun createUniqueId(): Long {

        return System.currentTimeMillis() +
                (0..999).random()
    }
}
