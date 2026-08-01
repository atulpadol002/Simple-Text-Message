package com.atul.messageapp.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SmsSender(
    context: Context
) {

    private val appContext =
        context.applicationContext

    fun sendSms(
        phoneNumber: String,
        message: String,
        onSentResult: (Boolean) -> Unit = {}
    ): Boolean {

        if (
            phoneNumber.isBlank() ||
            message.isBlank()
        ) {
            return false
        }

        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val smsManager =
            appContext.getSystemService(
                SmsManager::class.java
            ) ?: return false

        val messageParts =
            smsManager.divideMessage(message)

        if (messageParts.isEmpty()) {
            return false
        }

        val uniqueId =
            System.nanoTime()

        val sentAction =
            "${appContext.packageName}.action.SMS_SENT.$uniqueId"

        val completed =
            AtomicBoolean(false)

        val receivedCallbacks =
            AtomicInteger(0)

        val anyPartFailed =
            AtomicBoolean(false)

        val timeoutHandler =
            Handler(Looper.getMainLooper())

        lateinit var receiver: BroadcastReceiver

        fun cleanUp() {

            timeoutHandler.removeCallbacksAndMessages(
                uniqueId
            )

            try {

                appContext.unregisterReceiver(
                    receiver
                )

            } catch (_: IllegalArgumentException) {
            }
        }

        fun finish(
            success: Boolean
        ) {

            if (!completed.compareAndSet(false, true)) {
                return
            }

            cleanUp()

            onSentResult(success)
        }

        receiver =
            object : BroadcastReceiver() {

                override fun onReceive(
                    context: Context,
                    intent: Intent
                ) {

                    if (intent.action != sentAction) {
                        return
                    }

                    if (
                        resultCode !=
                        Activity.RESULT_OK
                    ) {
                        anyPartFailed.set(true)
                    }

                    val callbackCount =
                        receivedCallbacks.incrementAndGet()

                    if (
                        callbackCount >=
                        messageParts.size
                    ) {

                        finish(
                            success =
                                !anyPartFailed.get()
                        )
                    }
                }
            }

        return try {

            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(sentAction),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val sentPendingIntents =
                ArrayList<PendingIntent>()

            messageParts.forEachIndexed {
                    index,
                    _ ->

                val sentIntent =
                    Intent(sentAction).apply {

                        setPackage(
                            appContext.packageName
                        )

                        putExtra(
                            "part_index",
                            index
                        )
                    }

                val pendingIntent =
                    PendingIntent.getBroadcast(
                        appContext,
                        uniqueId.hashCode() + index,
                        sentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE
                    )

                sentPendingIntents.add(
                    pendingIntent
                )
            }

            if (messageParts.size == 1) {

                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    messageParts.first(),
                    sentPendingIntents.first(),
                    null
                )

            } else {

                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    ArrayList(messageParts),
                    sentPendingIntents,
                    null
                )
            }

            val timeoutRunnable =
                Runnable {

                    finish(
                        success = false
                    )
                }

            timeoutHandler.postAtTime(
                timeoutRunnable,
                uniqueId,
                System.currentTimeMillis() +
                        120_000L
            )

            true

        } catch (exception: Exception) {

            exception.printStackTrace()

            completed.set(true)
            cleanUp()

            false
        }
    }
}
