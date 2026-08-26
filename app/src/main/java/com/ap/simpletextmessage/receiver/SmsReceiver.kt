package com.ap.simpletextmessage.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.ap.simpletextmessage.data.preferences.BlockedNumbersPreferences
import com.ap.simpletextmessage.notifications.MessageNotificationManager
import com.ap.simpletextmessage.data.repository.SmsRepository
import com.ap.simpletextmessage.utils.ContactPresentationResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        if (
            intent.action !=
            Telephony.Sms.Intents.SMS_DELIVER_ACTION
        ) {
            return
        }

        val smsMessages =
            Telephony.Sms.Intents
                .getMessagesFromIntent(intent)

        if (smsMessages.isEmpty()) {
            return
        }

        val sender =
            smsMessages.first()
                .displayOriginatingAddress
                ?: smsMessages.first()
                    .originatingAddress
                ?: "Unknown"

        val blockedNumbersPreferences =
            BlockedNumbersPreferences(
                context.applicationContext
            )

        /*
         * Blocked number ka SMS inbox me
         * insert nahi kiya jayega.
         */
        if (
            blockedNumbersPreferences
                .isNumberBlocked(sender)
        ) {
            return
        }

        val messageBody =
            smsMessages.joinToString(
                separator = ""
            ) { smsMessage ->

                smsMessage.messageBody
                    ?: ""
            }

        val timestamp =
            smsMessages.first()
                .timestampMillis
        val receivedAt = System.currentTimeMillis()

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {

            val values =
                ContentValues().apply {

                    put(
                        Telephony.Sms.ADDRESS,
                        sender
                    )

                    put(
                        Telephony.Sms.BODY,
                        messageBody
                    )

                    put(
                        Telephony.Sms.DATE,
                        timestamp
                    )

                    put(
                        Telephony.Sms.DATE_SENT,
                        timestamp
                    )

                    put(
                        Telephony.Sms.READ,
                        0
                    )

                    put(
                        Telephony.Sms.SEEN,
                        0
                    )

                    put(
                        Telephony.Sms.TYPE,
                        Telephony.Sms
                            .MESSAGE_TYPE_INBOX
                    )
                }

            val insertedUri =
                context.contentResolver.insert(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    values
                )

            if (insertedUri == null) {
                return@launch
            }

            val threadId =
                resolveThreadId(
                    context = context,
                    insertedUri = insertedUri
                )

            if (threadId == null) return@launch
            SmsEventBus.notifySmsReceived(threadId)
            val notificationToken = MessageNotificationManager.beginIncoming(
                threadId = threadId,
                messageTimestamp = timestamp,
                receivedAt = receivedAt
            ) ?: return@launch
            val presentation = ContactPresentationResolver(context).resolve(sender)
            val totalUnread = SmsRepository(context).getConversations().sumOf { it.unreadCount }
            MessageNotificationManager.showIncoming(
                context = context.applicationContext,
                token = notificationToken,
                name = presentation.displayName,
                address = sender,
                body = messageBody,
                totalUnread = totalUnread
            )

            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {

                exception.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun resolveThreadId(
        context: Context,
        insertedUri: Uri
    ): Long? {

        return try {

            context.contentResolver.query(
                insertedUri,
                arrayOf(
                    Telephony.Sms.THREAD_ID
                ),
                null,
                null,
                null
            )?.use { cursor ->

                if (!cursor.moveToFirst()) {
                    return@use null
                }

                cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        Telephony.Sms.THREAD_ID
                    )
                ).takeIf { threadId ->
                    threadId > 0L
                }
            }

        } catch (exception: CancellationException) {
            throw exception
        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()
            null
        }
    }
}
