package com.ap.simpletextmessage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ap.simpletextmessage.data.preferences.ScheduledSmsPreferences
import com.ap.simpletextmessage.data.repository.MessageRepository
import com.ap.simpletextmessage.data.model.ScheduledSmsStatus
import com.ap.simpletextmessage.sms.ScheduledSmsScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledSmsReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val messageId =
            intent.getLongExtra(
                ScheduledSmsScheduler.EXTRA_MESSAGE_ID,
                -1L
            )

        val phoneNumber =
            intent.getStringExtra(
                ScheduledSmsScheduler.EXTRA_PHONE_NUMBER
            ).orEmpty()

        val messageBody =
            intent.getStringExtra(
                ScheduledSmsScheduler.EXTRA_MESSAGE
            ).orEmpty()

        if (
            messageId == -1L ||
            phoneNumber.isBlank() ||
            messageBody.isBlank()
        ) {
            return
        }

        val appContext =
            context.applicationContext

        val preferences =
            ScheduledSmsPreferences(
                appContext
            )

        /*
         * Message edit mode me hai to original
         * scheduled alarm se SMS send nahi hoga.
         */
        if (
            preferences.isMessageEditing(
                messageId
            )
        ) {
            return
        }

        /*
         * Message preferences me nahi hai to
         * schedule cancel/delete ho chuka hai.
         */
        val savedScheduledMessage =
            preferences.getScheduledMessage(
                messageId
            ) ?: return

        /*
         * Intent me purana message/time ho sakta hai.
         * Hamesha latest saved data use karenge.
         */
        val latestPhoneNumber =
            savedScheduledMessage.phoneNumber

        val latestMessageBody =
            savedScheduledMessage.message

        if (
            latestPhoneNumber.isBlank() ||
            latestMessageBody.isBlank()
        ) {
            return
        }

        val pendingResult =
            goAsync()

        CoroutineScope(
            Dispatchers.IO
        ).launch {

            val repository =
                MessageRepository(
                    appContext
                )

            try {

                /*
                 * Coroutine start hone tak edit mode activate
                 * ho gaya ho to dobara safety check.
                 */
                if (
                    preferences.isMessageEditing(
                        messageId
                    )
                ) {
                    pendingResult.finish()
                    return@launch
                }

                /*
                 * Schedule cancel/delete ho gaya ho to send nahi karna.
                 */
                val latestScheduledMessage =
                    preferences.getScheduledMessage(
                        messageId
                    )

                if (latestScheduledMessage == null) {
                    pendingResult.finish()
                    return@launch
                }

                val insertedMessageId =
                    repository.insertOutgoingMessage(
                        phoneNumber =
                            latestScheduledMessage
                                .phoneNumber,
                        body =
                            latestScheduledMessage
                                .message
                    )

                preferences.updateStatus(messageId, ScheduledSmsStatus.SENDING)
                ScheduledSmsEventBus.notify(messageId, ScheduledSmsStatus.SENDING)

                val handedOff =
                    repository.sendSms(
                        phoneNumber =
                            latestScheduledMessage
                                .phoneNumber,
                        message =
                            latestScheduledMessage
                                .message,
                        messageId = insertedMessageId,
                        scheduledId = messageId,
                        onSentResult = {}
                    )

                if (!handedOff) {

                    if (
                        insertedMessageId != -1L
                    ) {

                        repository.markMessageFailed(
                            insertedMessageId
                        )
                    }

                    preferences.updateStatus(messageId, ScheduledSmsStatus.FAILED)
                    ScheduledSmsEventBus.notify(messageId, ScheduledSmsStatus.FAILED)

                    pendingResult.finish()
                } else {
                    // The explicit SENT receiver owns the callback-driven terminal status.
                    // This alarm receiver only needs to stay alive through the SmsManager handoff.
                    pendingResult.finish()
                }

            } catch (
                exception: Exception
            ) {

                exception.printStackTrace()
                preferences.updateStatus(messageId, ScheduledSmsStatus.FAILED)
                ScheduledSmsEventBus.notify(messageId, ScheduledSmsStatus.FAILED)
                pendingResult.finish()
            }
        }
    }
}
