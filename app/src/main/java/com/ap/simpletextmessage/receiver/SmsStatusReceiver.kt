package com.ap.simpletextmessage.receiver

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.ap.simpletextmessage.data.datasource.MessageDataSource
import com.ap.simpletextmessage.data.model.ScheduledSmsStatus
import com.ap.simpletextmessage.data.preferences.ScheduledSmsPreferences
import com.ap.simpletextmessage.sms.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        if (requestId.isBlank()) return
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val partCount = intent.getIntExtra(EXTRA_PART_COUNT, 0)
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        val scheduledId = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)
        val callbackResult = resultCode
        val errorCode = intent.getIntExtra("errorCode", 0)
        val timeoutRequestCode = intent.getIntExtra(EXTRA_TIMEOUT_REQUEST_CODE, -1)

        if (intent.action == ACTION_SMS_DELIVERED) {
            Log.i(
                SmsSender.TAG,
                "delivered callback requestId=$requestId part=$partIndex/$partCount result=$callbackResult " +
                    "subscriptionId=$subscriptionId scheduledId=$scheduledId"
            )
            return
        }
        val terminalResult = if (intent.action == ACTION_SMS_TIMEOUT) {
            Log.e(SmsSender.TAG, "send timeout requestId=$requestId scheduledId=$scheduledId")
            SmsSendResultStore.timeout(context.applicationContext, requestId) ?: return
        } else {
            if (intent.action != ACTION_SMS_SENT || partIndex !in 0 until partCount) return
            val success = callbackResult == Activity.RESULT_OK
            Log.i(
                SmsSender.TAG,
                "sent callback requestId=$requestId part=$partIndex/$partCount success=$success " +
                    "result=${resultDescription(callbackResult)} errorCode=$errorCode " +
                    "subscriptionId=$subscriptionId scheduledId=$scheduledId"
            )
            SmsSendResultStore.record(
                context.applicationContext, requestId, partIndex, partCount, success
            ) ?: return
        }
        cancelTimeout(context.applicationContext, timeoutRequestCode)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (messageId >= 0L) {
                    val dataSource = MessageDataSource(context.applicationContext)
                    if (terminalResult) dataSource.markMessageSent(messageId)
                    else dataSource.markMessageFailed(messageId)
                }
                if (scheduledId >= 0L) {
                    val status = if (terminalResult) ScheduledSmsStatus.SENT else ScheduledSmsStatus.FAILED
                    ScheduledSmsPreferences(context.applicationContext).updateStatus(scheduledId, status)
                    ScheduledSmsEventBus.notify(scheduledId, status)
                }
            } catch (exception: RuntimeException) {
                Log.e(SmsSender.TAG, "status persistence failed requestId=$requestId", exception)
            } finally {
                SmsSender.completeRequest(requestId, terminalResult)
                pendingResult.finish()
            }
        }
    }

    private fun cancelTimeout(context: Context, requestCode: Int) {
        if (requestCode < 0) return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, SmsStatusReceiver::class.java).apply { action = ACTION_SMS_TIMEOUT },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun resultDescription(code: Int): String = when (code) {
        Activity.RESULT_OK -> "RESULT_OK"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "RESULT_ERROR_GENERIC_FAILURE"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "RESULT_ERROR_RADIO_OFF"
        SmsManager.RESULT_ERROR_NULL_PDU -> "RESULT_ERROR_NULL_PDU"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "RESULT_ERROR_NO_SERVICE"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "RESULT_ERROR_LIMIT_EXCEEDED"
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "RESULT_ERROR_FDN_CHECK_FAILURE"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "RESULT_ERROR_SHORT_CODE_NOT_ALLOWED"
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED"
        else -> "code=$code"
    }

    companion object {
        const val ACTION_SMS_SENT = "com.ap.simpletextmessage.action.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.ap.simpletextmessage.action.SMS_DELIVERED"
        const val ACTION_SMS_TIMEOUT = "com.ap.simpletextmessage.action.SMS_TIMEOUT"
        const val EXTRA_REQUEST_ID = "sms_request_id"
        const val EXTRA_PART_INDEX = "sms_part_index"
        const val EXTRA_PART_COUNT = "sms_part_count"
        const val EXTRA_MESSAGE_ID = "sms_message_id"
        const val EXTRA_SCHEDULED_ID = "sms_scheduled_id"
        const val EXTRA_SUBSCRIPTION_ID = "sms_subscription_id"
        const val EXTRA_TIMEOUT_REQUEST_CODE = "sms_timeout_request_code"
    }
}

private object SmsSendResultStore {
    private const val PREFS = "sms_send_callback_state"

    @Synchronized
    fun record(
        context: Context,
        requestId: String,
        partIndex: Int,
        partCount: Int,
        partSucceeded: Boolean
    ): Boolean? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefix = "$requestId."
        if (preferences.getBoolean(prefix + "complete", false)) return null
        val seen = preferences.getStringSet(prefix + "seen", emptySet()).orEmpty().toMutableSet()
        val failed = preferences.getBoolean(prefix + "failed", false) || !partSucceeded
        seen += partIndex.toString()
        if (seen.size < partCount) {
            preferences.edit()
                .putStringSet(prefix + "seen", seen)
                .putBoolean(prefix + "failed", failed)
                .commit()
            return null
        }
        preferences.edit()
            .remove(prefix + "seen")
            .remove(prefix + "failed")
            .putBoolean(prefix + "complete", true)
            .putLong(prefix + "completeAt", System.currentTimeMillis())
            .apply()
        return !failed
    }

    @Synchronized
    fun timeout(context: Context, requestId: String): Boolean? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefix = "$requestId."
        if (preferences.getBoolean(prefix + "complete", false)) return null
        preferences.edit()
            .remove(prefix + "seen")
            .remove(prefix + "failed")
            .putBoolean(prefix + "complete", true)
            .putLong(prefix + "completeAt", System.currentTimeMillis())
            .apply()
        return false
    }
}
