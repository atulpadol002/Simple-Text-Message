package com.atul.messageapp.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.atul.messageapp.data.preferences.BlockedNumbersPreferences

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

            context.contentResolver.insert(
                Telephony.Sms.Inbox.CONTENT_URI,
                values
            )

            SmsEventBus.notifySmsReceived()

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()
        }
    }
}