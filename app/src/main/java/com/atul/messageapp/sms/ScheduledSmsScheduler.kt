package com.atul.messageapp.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.atul.messageapp.data.model.ScheduledSms
import com.atul.messageapp.receiver.ScheduledSmsReceiver

class ScheduledSmsScheduler(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val alarmManager =
        appContext.getSystemService(
            AlarmManager::class.java
        )

    fun canScheduleExactAlarms(): Boolean {
        val alarmManager = alarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
    }

    fun schedule(
        scheduledSms: ScheduledSms
    ): Boolean {

        if (
            scheduledSms.phoneNumber.isBlank() ||
            scheduledSms.message.isBlank() ||
            scheduledSms.scheduledTime <=
            System.currentTimeMillis()
        ) {
            return false
        }

        val alarmManager =
            alarmManager ?: return false

        if (!canScheduleExactAlarms()) {
            return false
        }

        val pendingIntent =
            createPendingIntent(
                scheduledSms = scheduledSms
            )

        return try {

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledSms.scheduledTime,
                pendingIntent
            )

            true

        } catch (exception: Exception) {

            exception.printStackTrace()
            false
        }
    }

    fun cancel(
        messageId: Long
    ) {

        val alarmManager =
            alarmManager ?: return

        val intent =
            Intent(
                appContext,
                ScheduledSmsReceiver::class.java
            )

        val pendingIntent =
            PendingIntent.getBroadcast(
                appContext,
                messageId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or
                        PendingIntent.FLAG_IMMUTABLE
            )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun createPendingIntent(
        scheduledSms: ScheduledSms
    ): PendingIntent {

        val intent =
            Intent(
                appContext,
                ScheduledSmsReceiver::class.java
            ).apply {

                putExtra(
                    EXTRA_MESSAGE_ID,
                    scheduledSms.id
                )

                putExtra(
                    EXTRA_PHONE_NUMBER,
                    scheduledSms.phoneNumber
                )

                putExtra(
                    EXTRA_MESSAGE,
                    scheduledSms.message
                )
            }

        return PendingIntent.getBroadcast(
            appContext,
            scheduledSms.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {

        const val EXTRA_MESSAGE_ID =
            "scheduled_message_id"

        const val EXTRA_PHONE_NUMBER =
            "scheduled_phone_number"

        const val EXTRA_MESSAGE =
            "scheduled_message_body"
    }
}
