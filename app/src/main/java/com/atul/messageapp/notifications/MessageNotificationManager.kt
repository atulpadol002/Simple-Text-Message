package com.atul.messageapp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
    private const val GROUP_KEY = "incoming_messages_group"

    @Volatile private var activeThreadId: Long? = null

    fun setActiveThread(threadId: Long?) { activeThreadId = threadId?.takeIf { it > 0L } }

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

    fun showIncoming(context: Context, threadId: Long, name: String, address: String, body: String, totalUnread: Int = 0) {
        if (threadId <= 0L || activeThreadId == threadId) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            data = android.net.Uri.parse("messageapp://notification/chat/$threadId")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_CONTACT_NAME, name)
            putExtra(EXTRA_ADDRESS, address)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId(threadId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(threadId), notification)
    }

    fun cancelThread(context: Context, threadId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(threadId))
    }

    fun updateUnreadCount(context: Context, totalUnread: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        NotificationManagerCompat.from(context).activeNotifications.forEach { status ->
            status.notification.extras?.let { extras ->
                val updated = NotificationCompat.Builder(context, status.notification)
                    .setNumber(totalUnread)
                    .setGroup(GROUP_KEY)
                    .build()
                NotificationManagerCompat.from(context).notify(status.id, updated)
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

    private fun notificationId(threadId: Long): Int = 0x4d530000 xor (threadId xor (threadId ushr 32)).toInt()
}
