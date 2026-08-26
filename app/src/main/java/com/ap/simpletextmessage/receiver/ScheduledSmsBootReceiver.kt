package com.ap.simpletextmessage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ap.simpletextmessage.data.preferences.ScheduledSmsPreferences
import com.ap.simpletextmessage.sms.ScheduledSmsScheduler
import com.ap.simpletextmessage.data.model.ScheduledSmsStatus

class ScheduledSmsBootReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        if (
            intent.action !=
            Intent.ACTION_BOOT_COMPLETED
        ) {
            return
        }

        val preferences =
            ScheduledSmsPreferences(context)

        val scheduler =
            ScheduledSmsScheduler(context)

        preferences
            .getScheduledMessages()
            .forEach { scheduledSms ->

                if (scheduledSms.status == ScheduledSmsStatus.SENDING) {
                    preferences.updateStatus(scheduledSms.id, ScheduledSmsStatus.FAILED)
                    return@forEach
                }
                if (scheduledSms.status != ScheduledSmsStatus.SCHEDULED) return@forEach

                preferences.clearMessageEditing(
                    scheduledSms.id
                )

                val messageToSchedule =
                    if (
                        scheduledSms.scheduledTime >
                        System.currentTimeMillis()
                    ) {

                        scheduledSms

                    } else {

                        scheduledSms.copy(
                            scheduledTime =
                                System.currentTimeMillis() +
                                        RECOVERY_DELAY_MILLIS
                        )
                    }

                scheduler.schedule(
                    messageToSchedule
                )
            }
    }

    companion object {

        private const val RECOVERY_DELAY_MILLIS =
            1_000L
    }
}
