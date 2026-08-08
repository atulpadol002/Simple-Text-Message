package com.ap.messages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ap.messages.data.preferences.ScheduledSmsPreferences
import com.ap.messages.data.repository.MessageRepository
import com.ap.messages.sms.ScheduledSmsScheduler
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

                val handedOff =
                    repository.sendSms(
                        phoneNumber =
                            latestScheduledMessage
                                .phoneNumber,
                        message =
                            latestScheduledMessage
                                .message,
                        onSentResult = { sent ->

                            CoroutineScope(
                                Dispatchers.IO
                            ).launch {

                                try {

                                    if (
                                        insertedMessageId != -1L
                                    ) {

                                        if (sent) {

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

                                    /*
                                     * Success hone par scheduled list se
                                     * message remove hoga.
                                     */
                                    if (sent) {

                                        preferences
                                            .deleteScheduledMessage(
                                                messageId
                                            )
                                    }

                                } finally {

                                    pendingResult.finish()
                                }
                            }
                        }
                    )

                if (!handedOff) {

                    if (
                        insertedMessageId != -1L
                    ) {

                        repository.markMessageFailed(
                            insertedMessageId
                        )
                    }

                    pendingResult.finish()
                }

            } catch (
                exception: Exception
            ) {

                exception.printStackTrace()
                pendingResult.finish()
            }
        }
    }
}