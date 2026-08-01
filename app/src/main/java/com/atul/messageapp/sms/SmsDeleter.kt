package com.atul.messageapp.sms

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.atul.messageapp.data.model.DeletedConversation
import com.atul.messageapp.data.preferences.ArchivePreferences
import com.atul.messageapp.data.preferences.StarredMessagesPreferences
import com.atul.messageapp.data.preferences.PinnedConversationsPreferences
import com.atul.messageapp.data.repository.RecycleBinRepository
import com.atul.messageapp.data.repository.SmsRepository
import com.atul.messageapp.utils.getContactName

class SmsDeleter(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val archivePreferences =
        ArchivePreferences(appContext)

    private val starredMessagesPreferences =
        StarredMessagesPreferences(appContext)

    private val pinnedConversationsPreferences =
        PinnedConversationsPreferences(appContext)

    private val smsRepository =
        SmsRepository(appContext)

    private val recycleBinRepository =
        RecycleBinRepository(appContext)

    fun deleteConversation(
        threadId: Long
    ): Boolean {

        if (threadId <= 0L) {
            return false
        }

        var createdSnapshotId: Long? = null
        var providerDeleteCompleted = false

        return try {

            val messages =
                smsRepository.getConversationSnapshotMessages(
                    threadId
                ) ?: return false

            val address = messages.firstOrNull()
                ?.address
                .orEmpty()

            val snapshot = DeletedConversation(
                originalThreadId = threadId,
                address = address,
                cachedDisplayName = getContactName(
                    context = appContext,
                    phoneNumber = address
                ),
                deletedAt = System.currentTimeMillis()
            )

            val snapshotId =
                recycleBinRepository
                    .saveConversationSnapshotAndGetId(
                        conversation = snapshot,
                        messages = messages
                    )
                    ?: return false

            createdSnapshotId = snapshotId

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
                removeSnapshot(snapshotId)

                return false
            }

            providerDeleteCompleted = true

            archivePreferences.unarchiveConversation(
                threadId
            )

            pinnedConversationsPreferences.removePinnedThreadIds(setOf(threadId))

            starredMessagesPreferences
                .removeStarredMessageIds(
                    messages.map { message ->
                        message.originalMessageId
                    }.toSet()
                )

            true

        } catch (
            exception: SecurityException
        ) {

            if (!providerDeleteCompleted) {
                createdSnapshotId?.let(::removeSnapshot)
            }

            Log.e(
                "SmsDeleter",
                "SMS delete permission denied",
                exception
            )

            false

        } catch (
            exception: Exception
        ) {

            if (!providerDeleteCompleted) {
                createdSnapshotId?.let(::removeSnapshot)
            }

            Log.e(
                "SmsDeleter",
                "Delete conversation failed",
                exception
            )

            false
        }
    }

    private fun removeSnapshot(
        recycleBinId: Long
    ) {
        try {
            recycleBinRepository
                .deleteSnapshotPermanently(
                    recycleBinId
                )
        } catch (exception: Exception) {
            Log.e(
                "SmsDeleter",
                "Failed to remove Recycle Bin snapshot",
                exception
            )
        }
    }

}
