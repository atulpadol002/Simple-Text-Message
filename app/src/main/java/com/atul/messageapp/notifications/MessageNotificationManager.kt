package com.atul.messageapp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.atul.messageapp.MainActivity
import com.atul.messageapp.R

object MessageNotificationManager {
    const val ACTION_OPEN_CHAT = "com.atul.messageapp.action.OPEN_NOTIFICATION_CHAT"
    const val CHANNEL_ID = "incoming_messages"
    const val EXTRA_THREAD_ID = "notification_thread_id"
    const val EXTRA_CONTACT_NAME = "notification_contact_name"
    const val EXTRA_ADDRESS = "notification_address"
    const val EXTRA_NAVIGATION_TOKEN = "notification_navigation_token"
    private const val GROUP_KEY = "incoming_messages_group"
    private const val EXTRA_RECEIVED_AT = "notification_received_at"
    private const val EXTRA_MESSAGE_TIMESTAMP = "notification_message_timestamp"
    private const val MAX_TRACKED_THREADS = 128

    class IncomingToken internal constructor(
        val threadId: Long,
        val messageTimestamp: Long,
        val receivedAt: Long,
        internal val generation: Long,
        internal val sequence: Long
    ) {
        val navigationToken: String = "$threadId:$messageTimestamp:$receivedAt:$sequence"
    }

    private val stateLock = Any()
    private var activeThreadId: Long? = null
    private val generationByThread = linkedMapOf<Long, Long>()
    private val consumedAtByThread = linkedMapOf<Long, Long>()
    private val latestIncomingAtByThread = linkedMapOf<Long, Long>()
    private val latestIncomingSequenceByThread = linkedMapOf<Long, Long>()
    private var nextIncomingSequence = 0L

    fun beginIncoming(
        threadId: Long,
        messageTimestamp: Long,
        receivedAt: Long
    ): IncomingToken? = synchronized(stateLock) {
        if (threadId <= 0L || activeThreadId == threadId) return@synchronized null
        if (receivedAt <= consumedAtByThread.getOrDefault(threadId, Long.MIN_VALUE)) {
            return@synchronized null
        }
        val sequence = ++nextIncomingSequence
        val latestReceivedAt = latestIncomingAtByThread.getOrDefault(threadId, Long.MIN_VALUE)
        if (receivedAt >= latestReceivedAt) {
            latestIncomingAtByThread[threadId] = receivedAt
            latestIncomingSequenceByThread[threadId] = sequence
            trimThreadStateLocked()
        }
        IncomingToken(
            threadId = threadId,
            messageTimestamp = messageTimestamp,
            receivedAt = receivedAt,
            generation = generationByThread.getOrDefault(threadId, 0L),
            sequence = sequence
        )
    }

    fun activateThread(context: Context, threadId: Long) {
        if (threadId <= 0L) return
        synchronized(stateLock) {
            activeThreadId = threadId
            consumeThreadLocked(context, threadId)
        }
    }

    fun deactivateThread(threadId: Long) {
        synchronized(stateLock) {
            if (activeThreadId == threadId) activeThreadId = null
        }
    }

    fun consumeThread(context: Context, threadId: Long) {
        if (threadId <= 0L) return
        synchronized(stateLock) {
            consumeThreadLocked(context, threadId)
        }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Incoming messages", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming SMS messages"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build())
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showIncoming(
        context: Context,
        token: IncomingToken,
        name: String,
        address: String,
        body: String,
        totalUnread: Int = 0
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            data = android.net.Uri.parse("messageapp://notification/chat/${token.threadId}")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_THREAD_ID, token.threadId)
            putExtra(EXTRA_CONTACT_NAME, name)
            putExtra(EXTRA_ADDRESS, address)
            putExtra(EXTRA_NAVIGATION_TOKEN, token.navigationToken)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId(token.threadId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationExtras = Bundle().apply {
            putLong(EXTRA_THREAD_ID, token.threadId)
            putLong(EXTRA_RECEIVED_AT, token.receivedAt)
            putLong(EXTRA_MESSAGE_TIMESTAMP, token.messageTimestamp)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_message)
            .setContentTitle(name.ifBlank { address })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setNumber(totalUnread)
            .setGroup(GROUP_KEY)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addExtras(notificationExtras)
            .build()
        synchronized(stateLock) {
            if (!isEligibleLocked(token)) return
            NotificationManagerCompat.from(context).notify(
                notificationId(token.threadId),
                notification
            )
        }
    }

    fun cancelThread(context: Context, threadId: Long) {
        consumeThread(context, threadId)
    }

    fun updateUnreadCount(context: Context, totalUnread: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.activeNotifications.forEach { status ->
            val notification = status.notification
            val isGroupSummary =
                notification.group == GROUP_KEY &&
                    notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0
            if (isGroupSummary) {
                if (totalUnread <= 0) {
                    notificationManager.cancel(status.id)
                } else {
                    synchronized(stateLock) {
                        notificationManager.notify(
                            status.id,
                            NotificationCompat.Builder(context, notification)
                                .setNumber(totalUnread)
                                .build()
                        )
                    }
                }
                return@forEach
            }

            val extras = notification.extras ?: return@forEach
            val threadId = extras.getLong(EXTRA_THREAD_ID, 0L)
            val receivedAt = extras.getLong(EXTRA_RECEIVED_AT, Long.MIN_VALUE)
            if (threadId <= 0L || status.id != notificationId(threadId)) return@forEach

            synchronized(stateLock) {
                if (!isPostedChildEligibleLocked(threadId, receivedAt)) {
                    notificationManager.cancel(status.id)
                } else {
                    notificationManager.notify(
                        status.id,
                        NotificationCompat.Builder(context, notification)
                            .setNumber(totalUnread)
                            .setGroup(GROUP_KEY)
                            .build()
                    )
                }
            }
        }
    }

    fun openChannelSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun openAppNotificationSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun consumeThreadLocked(context: Context, threadId: Long) {
        generationByThread[threadId] = generationByThread.getOrDefault(threadId, 0L) + 1L
        consumedAtByThread[threadId] = System.currentTimeMillis()
        trimThreadStateLocked()
        NotificationManagerCompat.from(context).cancel(notificationId(threadId))
    }

    private fun isEligibleLocked(token: IncomingToken): Boolean =
        activeThreadId != token.threadId &&
            token.generation == generationByThread.getOrDefault(token.threadId, 0L) &&
            token.receivedAt > consumedAtByThread.getOrDefault(token.threadId, Long.MIN_VALUE) &&
            token.receivedAt == latestIncomingAtByThread.getOrDefault(
                token.threadId,
                Long.MIN_VALUE
            ) &&
            token.sequence == latestIncomingSequenceByThread.getOrDefault(
                token.threadId,
                Long.MIN_VALUE
            )

    private fun isPostedChildEligibleLocked(threadId: Long, receivedAt: Long): Boolean =
        activeThreadId != threadId &&
            receivedAt > consumedAtByThread.getOrDefault(threadId, Long.MIN_VALUE)

    private fun trimThreadStateLocked() {
        while (generationByThread.size > MAX_TRACKED_THREADS) {
            generationByThread.remove(generationByThread.keys.first())
        }
        while (consumedAtByThread.size > MAX_TRACKED_THREADS) {
            consumedAtByThread.remove(consumedAtByThread.keys.first())
        }
        while (latestIncomingAtByThread.size > MAX_TRACKED_THREADS) {
            latestIncomingAtByThread.remove(latestIncomingAtByThread.keys.first())
        }
        while (latestIncomingSequenceByThread.size > MAX_TRACKED_THREADS) {
            latestIncomingSequenceByThread.remove(latestIncomingSequenceByThread.keys.first())
        }
    }

    private fun notificationId(threadId: Long): Int =
        0x4d530000 xor (threadId xor (threadId ushr 32)).toInt()
}
