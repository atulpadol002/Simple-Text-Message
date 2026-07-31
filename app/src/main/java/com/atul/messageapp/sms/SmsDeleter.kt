package com.atul.messageapp.sms

import android.content.Context
import android.provider.Telephony
import android.util.Log

class SmsDeleter(
    context: Context
) {

    private val appContext =
        context.applicationContext

    fun deleteConversation(
        threadId: Long
    ): Boolean {

        if (threadId <= 0L) {
            return false
        }

        return try {

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

            deletedRows > 0

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
}