package com.atul.messageapp.sms

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.atul.messageapp.data.preferences.ArchivePreferences
import com.atul.messageapp.data.preferences.StarredMessagesPreferences

class SmsDeleter(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val archivePreferences =
        ArchivePreferences(appContext)

    private val starredMessagesPreferences =
        StarredMessagesPreferences(appContext)

    fun deleteConversation(
        threadId: Long
    ): Boolean {

        if (threadId <= 0L) {
            return false
        }

        return try {

            val messageIds =
                getMessageIdsForThread(
                    threadId
                ) ?: return false

            val deletedRows =
                appContext.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID}=?",
                    arrayOf(
                        threadId.toString()
                    )
                )

            Log.d(
                "SmsDeleter",
                "Deleted rows: $deletedRows"
            )

            if (deletedRows <= 0) {
                return false
            }

            archivePreferences.unarchiveConversation(
                threadId
            )

            starredMessagesPreferences
                .removeStarredMessageIds(
                    messageIds
                )

            true

        } catch (
            exception: SecurityException
        ) {

            Log.e(
                "SmsDeleter",
                "SMS delete permission denied",
                exception
            )

            false

        } catch (
            exception: Exception
        ) {

            Log.e(
                "SmsDeleter",
                "Delete conversation failed",
                exception
            )

            false
        }
    }

    private fun getMessageIdsForThread(
        threadId: Long
    ): Set<Long>? {

        val messageIds =
            mutableSetOf<Long>()

        val cursor =
            appContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID
                ),
                "${Telephony.Sms.THREAD_ID}=?",
                arrayOf(
                    threadId.toString()
                ),
                null
            ) ?: return null

        cursor.use {

            val messageIdIndex =
                it.getColumnIndexOrThrow(
                    Telephony.Sms._ID
                )

            while (it.moveToNext()) {
                messageIds.add(
                    it.getLong(
                        messageIdIndex
                    )
                )
            }
        }

        return messageIds
    }
}
