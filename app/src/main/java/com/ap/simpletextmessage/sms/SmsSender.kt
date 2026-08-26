package com.ap.simpletextmessage.sms

import android.Manifest
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.os.SystemClock
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ap.simpletextmessage.receiver.SmsStatusReceiver
import com.ap.simpletextmessage.utils.isReplyCapableAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SmsSender(context: Context) {
    private val appContext = context.applicationContext

    fun sendSms(
        phoneNumber: String,
        message: String,
        messageId: Long = -1L,
        scheduledId: Long = -1L,
        onSentResult: (Boolean) -> Unit = {}
    ): Boolean {
        val destination = phoneNumber.trim()
        if (!isReplyCapableAddress(destination) || message.isBlank()) {
            Log.e(TAG, "send rejected invalidDestination=${!isReplyCapableAddress(destination)} blankBody=${message.isBlank()} scheduledId=$scheduledId")
            return false
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "send rejected SEND_SMS permission missing scheduledId=$scheduledId")
            return false
        }

        val defaultPackage = Telephony.Sms.getDefaultSmsPackage(appContext)
        if (defaultPackage != appContext.packageName) {
            Log.w(TAG, "send request while app is not default SMS package default=$defaultPackage")
        }

        val (smsManager, subscriptionId) = resolveSmsManager() ?: run {
            Log.e(TAG, "send rejected SmsManager unavailable scheduledId=$scheduledId")
            return false
        }
        val parts = runCatching { smsManager.divideMessage(message) }.getOrElse {
            Log.e(TAG, "divideMessage failed scheduledId=$scheduledId", it)
            return false
        }
        if (parts.isEmpty()) {
            Log.e(TAG, "send rejected no message parts scheduledId=$scheduledId")
            return false
        }

        val requestId = UUID.randomUUID().toString()
        val requestCodeBase = NEXT_REQUEST_CODE.getAndAdd(parts.size * 2 + 2)
        callbacks[requestId] = onSentResult
        Log.i(
            TAG,
            "send request requestId=$requestId messageId=$messageId scheduledId=$scheduledId " +
                "subscriptionId=$subscriptionId multipartCount=${parts.size}"
        )

        val sentIntents = ArrayList<PendingIntent>(parts.size)
        val deliveredIntents = ArrayList<PendingIntent>(parts.size)
        parts.indices.forEach { index ->
            sentIntents += statusPendingIntent(
                SmsStatusReceiver.ACTION_SMS_SENT, requestCodeBase + index * 2, requestId,
                index, parts.size, messageId, scheduledId, subscriptionId,
                requestCodeBase + parts.size * 2
            )
            deliveredIntents += statusPendingIntent(
                SmsStatusReceiver.ACTION_SMS_DELIVERED, requestCodeBase + index * 2 + 1, requestId,
                index, parts.size, messageId, scheduledId, subscriptionId,
                requestCodeBase + parts.size * 2
            )
        }

        return try {
            if (parts.size == 1) {
                smsManager.sendTextMessage(
                    destination, null, parts.first(), sentIntents.first(), deliveredIntents.first()
                )
            } else {
                smsManager.sendMultipartTextMessage(
                    destination, null, ArrayList(parts), sentIntents, deliveredIntents
                )
            }
            scheduleTimeout(
                requestCode = requestCodeBase + parts.size * 2,
                requestId = requestId,
                messageId = messageId,
                scheduledId = scheduledId,
                subscriptionId = subscriptionId
            )
            true
        } catch (exception: SecurityException) {
            callbacks.remove(requestId)
            Log.e(TAG, "send handoff SecurityException requestId=$requestId scheduledId=$scheduledId", exception)
            false
        } catch (exception: IllegalArgumentException) {
            callbacks.remove(requestId)
            Log.e(TAG, "send handoff invalid arguments requestId=$requestId scheduledId=$scheduledId", exception)
            false
        } catch (exception: RuntimeException) {
            callbacks.remove(requestId)
            Log.e(TAG, "send handoff runtime failure requestId=$requestId scheduledId=$scheduledId", exception)
            false
        }
    }

    private fun statusPendingIntent(
        action: String,
        requestCode: Int,
        requestId: String,
        partIndex: Int,
        partCount: Int,
        messageId: Long,
        scheduledId: Long,
        subscriptionId: Int,
        timeoutRequestCode: Int
    ): PendingIntent {
        val intent = Intent(appContext, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(SmsStatusReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsStatusReceiver.EXTRA_PART_COUNT, partCount)
            putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
            putExtra(SmsStatusReceiver.EXTRA_SCHEDULED_ID, scheduledId)
            putExtra(SmsStatusReceiver.EXTRA_SUBSCRIPTION_ID, subscriptionId)
            putExtra(SmsStatusReceiver.EXTRA_TIMEOUT_REQUEST_CODE, timeoutRequestCode)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleTimeout(
        requestCode: Int,
        requestId: String,
        messageId: Long,
        scheduledId: Long,
        subscriptionId: Int
    ) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(appContext, SmsStatusReceiver::class.java).apply {
            action = SmsStatusReceiver.ACTION_SMS_TIMEOUT
            putExtra(SmsStatusReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
            putExtra(SmsStatusReceiver.EXTRA_SCHEDULED_ID, scheduledId)
            putExtra(SmsStatusReceiver.EXTRA_SUBSCRIPTION_ID, subscriptionId)
            putExtra(SmsStatusReceiver.EXTRA_TIMEOUT_REQUEST_CODE, requestCode)
        }
        val timeoutIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + SEND_TIMEOUT_MILLIS,
            timeoutIntent
        )
    }

    private fun resolveSmsManager(): Pair<SmsManager, Int>? {
        val systemManager = appContext.getSystemService(SmsManager::class.java) ?: return null
        val defaultSubscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        return if (SubscriptionManager.isValidSubscriptionId(defaultSubscriptionId)) {
            @Suppress("DEPRECATION")
            systemManager.createForSubscriptionId(defaultSubscriptionId) to defaultSubscriptionId
        } else {
            Log.w(TAG, "no default SMS subscription selected; using platform default SmsManager")
            systemManager to SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    companion object {
        const val TAG = "SimpleTextSms"
        private val NEXT_REQUEST_CODE = AtomicInteger((System.currentTimeMillis() and 0x3fffffff).toInt())
        private val callbacks = ConcurrentHashMap<String, (Boolean) -> Unit>()
        private const val SEND_TIMEOUT_MILLIS = 120_000L

        fun completeRequest(requestId: String, success: Boolean) {
            callbacks.remove(requestId)?.invoke(success)
        }
    }
}
