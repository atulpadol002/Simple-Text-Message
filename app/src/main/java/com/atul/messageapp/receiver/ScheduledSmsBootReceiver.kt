package com.atul.messageapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.atul.messageapp.data.preferences.ScheduledSmsPreferences
import com.atul.messageapp.sms.ScheduledSmsScheduler

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

        val currentTime =
            System.currentTimeMillis()

        preferences
            .getScheduledMessages()
            .filter {
                it.scheduledTime > currentTime
            }
            .forEach { scheduledSms ->
                scheduler.schedule(
                    scheduledSms
                )
            }
    }
}