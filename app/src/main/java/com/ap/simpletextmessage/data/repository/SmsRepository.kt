package com.ap.simpletextmessage.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import com.ap.simpletextmessage.data.model.DeletedMessage
import com.ap.simpletextmessage.data.model.SmsConversation
import com.ap.simpletextmessage.data.model.SmsMessage

class SmsRepository(
    private val context: Context
) {

    fun resolveThreadIdForAddress(address: String): Long {
        val cleanAddress = address.trim()
        if (cleanAddress.isEmpty()) return 0L

        getConversations().firstOrNull { conversation ->
            phoneNumbersMatch(conversation.address, cleanAddress)
        }?.let { return it.threadId }

        val normalized = PhoneNumberUtils.normalizeNumber(cleanAddress).ifBlank { cleanAddress }
        return try {
            Telephony.Threads.getOrCreateThreadId(context, normalized)
        } catch (exception: Exception) {
            exception.printStackTrace()
            0L
        }
    }

    private fun phoneNumbersMatch(first: String, second: String): Boolean {
        val normalizedFirst = PhoneNumberUtils.normalizeNumber(first)
        val normalizedSecond = PhoneNumberUtils.normalizeNumber(second)
        if (normalizedFirst.isNotEmpty() && normalizedFirst == normalizedSecond) return true

        val firstDigits = normalizedFirst.filter(Char::isDigit)
        val secondDigits = normalizedSecond.filter(Char::isDigit)
        return firstDigits.length >= 10 && secondDigits.length >= 10 &&
            firstDigits.takeLast(10) == secondDigits.takeLast(10)
    }

    fun getConversations(): List<SmsConversation> =
        queryConversations(maxRows = null)

    /**
     * Returns a fast first snapshot from the newest SMS rows. Counts in this snapshot are
     * intentionally provisional; [getConversations] replaces them after the full background scan.
     */
    fun getRecentConversations(maxRows: Int): List<SmsConversation> =
        queryConversations(maxRows = maxRows.coerceAtLeast(1))

    private fun queryConversations(maxRows: Int?): List<SmsConversation> {

        val latestConversationByThread =
            linkedMapOf<Long, SmsConversation>()

        val unreadCountByThread =
            mutableMapOf<Long, Int>()

        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )

        val sortOrder =
            "${Telephony.Sms.DATE} DESC"

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->

            val threadIdIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.THREAD_ID
                )

            val addressIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.ADDRESS
                )

            val bodyIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.BODY
                )

            val dateIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.DATE
                )

            val readIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.READ
                )

            var scannedRows = 0
            while ((maxRows == null || scannedRows < maxRows) && cursor.moveToNext()) {
                scannedRows++

                val threadId =
                    cursor.getLong(threadIdIndex)

                val isRead =
                    cursor.getInt(readIndex) == 1

                if (!isRead) {
                    unreadCountByThread[threadId] =
                        unreadCountByThread
                            .getOrDefault(threadId, 0) + 1
                }

                if (threadId !in latestConversationByThread) {

                    latestConversationByThread[threadId] =
                        SmsConversation(
                            threadId = threadId,
                            address = cursor
                                .getString(addressIndex)
                                ?: "Unknown",
                            body = cursor
                                .getString(bodyIndex)
                                ?: "",
                            date = cursor
                                .getLong(dateIndex),
                            read = isRead,
                            unreadCount = 0
                        )
                }
            }
        }

        return latestConversationByThread
            .values
            .map { conversation ->

                val unreadCount =
                    unreadCountByThread
                        .getOrDefault(
                            conversation.threadId,
                            0
                        )

                conversation.copy(
                    read = unreadCount == 0,
                    unreadCount = unreadCount
                )
            }
    }

    fun getMessages(
        threadId: Long
    ): List<SmsMessage> {

        val messages =
            mutableListOf<SmsMessage>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val selection =
            "${Telephony.Sms.THREAD_ID} = ?"

        val selectionArgs =
            arrayOf(threadId.toString())

        val sortOrder =
            "${Telephony.Sms.DATE} ASC"

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->

            val idIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms._ID
                )

            val threadIdIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.THREAD_ID
                )

            val addressIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.ADDRESS
                )

            val bodyIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.BODY
                )

            val dateIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.DATE
                )

            val typeIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.TYPE
                )

            while (cursor.moveToNext()) {

                messages.add(
                    SmsMessage(
                        id = cursor.getLong(idIndex),
                        threadId = cursor.getLong(
                            threadIdIndex
                        ),
                        address = cursor
                            .getString(addressIndex)
                            ?: "Unknown",
                        body = cursor
                            .getString(bodyIndex)
                            ?: "",
                        date = cursor
                            .getLong(dateIndex),
                        type = cursor
                            .getInt(typeIndex)
                    )
                )
            }
        }

        return messages
    }

    fun getMessagesByIds(messageIds: Set<Long>): List<SmsMessage> {
        if (messageIds.isEmpty()) return emptyList()
        val messages = mutableListOf<SmsMessage>()
        messageIds.chunked(500).forEach { ids ->
            val placeholders = ids.joinToString(",") { "?" }
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE
                ),
                "${Telephony.Sms._ID} IN ($placeholders)",
                ids.map(Long::toString).toTypedArray(),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (cursor.moveToNext()) {
                    messages += SmsMessage(
                        cursor.getLong(idIndex), cursor.getLong(threadIndex),
                        cursor.getString(addressIndex).orEmpty(), cursor.getString(bodyIndex).orEmpty(),
                        cursor.getLong(dateIndex), cursor.getInt(typeIndex)
                    )
                }
            }
        }
        return messages.sortedByDescending { it.date }
    }

    fun getConversationSnapshotMessages(
        threadId: Long
    ): List<DeletedMessage>? {
        val messages = mutableListOf<DeletedMessage>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SEEN,
            Telephony.Sms.STATUS,
            Telephony.Sms.SERVICE_CENTER,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        return try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.THREAD_ID}=?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
            ) ?: return null

            cursor.use {
                val idIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms._ID
                )
                val threadIdIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.THREAD_ID
                )
                val addressIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.ADDRESS
                )
                val bodyIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.BODY
                )
                val dateIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.DATE
                )
                val sentDateIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.DATE_SENT
                )
                val typeIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.TYPE
                )
                val readIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.READ
                )
                val seenIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.SEEN
                )
                val statusIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.STATUS
                )
                val serviceCenterIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.SERVICE_CENTER
                )
                val subscriptionIdIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.SUBSCRIPTION_ID
                )

                while (it.moveToNext()) {
                    messages.add(
                        DeletedMessage(
                            originalMessageId = it.getLong(idIndex),
                            originalThreadId = it.getLong(threadIdIndex),
                            address = it.getString(addressIndex).orEmpty(),
                            body = it.getString(bodyIndex).orEmpty(),
                            date = it.getLong(dateIndex),
                            sentDate = if (it.isNull(sentDateIndex)) {
                                null
                            } else {
                                it.getLong(sentDateIndex)
                            },
                            type = it.getInt(typeIndex),
                            read = it.getInt(readIndex) == 1,
                            seen = it.getInt(seenIndex) == 1,
                            status = if (it.isNull(statusIndex)) {
                                null
                            } else {
                                it.getInt(statusIndex)
                            },
                            serviceCenter = if (
                                it.isNull(serviceCenterIndex)
                            ) {
                                null
                            } else {
                                it.getString(serviceCenterIndex)
                            },
                            subscriptionId = if (
                                it.isNull(subscriptionIdIndex)
                            ) {
                                null
                            } else {
                                it.getInt(subscriptionIdIndex)
                            }
                        )
                    )
                }
            }

            messages
        } catch (exception: SecurityException) {
            exception.printStackTrace()
            null
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        }
    }

    fun restoredMessageExists(
        message: DeletedMessage
    ): Boolean? {
        val selection = buildString {
            append(
                "${Telephony.Sms.ADDRESS}=? AND " +
                        "${Telephony.Sms.BODY}=? AND " +
                        "${Telephony.Sms.DATE}=? AND " +
                        "${Telephony.Sms.TYPE}=?"
            )

            if (message.sentDate != null) {
                append(" AND ${Telephony.Sms.DATE_SENT}=?")
            }
        }

        val selectionArgs = buildList {
            add(message.address)
            add(message.body)
            add(message.date.toString())
            add(message.type.toString())
            message.sentDate?.let { sentDate ->
                add(sentDate.toString())
            }
        }.toTypedArray()

        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (exception: SecurityException) {
            exception.printStackTrace()
            null
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        }
    }

    fun restoreMessage(
        message: DeletedMessage
    ): Boolean {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, message.address)
            put(Telephony.Sms.BODY, message.body)
            put(Telephony.Sms.DATE, message.date)
            message.sentDate?.let { sentDate ->
                put(Telephony.Sms.DATE_SENT, sentDate)
            }
            put(Telephony.Sms.TYPE, message.type)
            put(Telephony.Sms.READ, if (message.read) 1 else 0)
            put(Telephony.Sms.SEEN, if (message.seen) 1 else 0)
            message.status?.let { status ->
                put(Telephony.Sms.STATUS, status)
            }
            message.serviceCenter?.let { serviceCenter ->
                put(Telephony.Sms.SERVICE_CENTER, serviceCenter)
            }
            message.subscriptionId?.let { subscriptionId ->
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
        }

        return try {
            context.contentResolver.insert(
                message.destinationUri(),
                values
            ) != null
        } catch (exception: SecurityException) {
            exception.printStackTrace()
            false
        } catch (exception: Exception) {
            exception.printStackTrace()
            false
        }
    }

    private fun DeletedMessage.destinationUri() =
        when (type) {
            Telephony.Sms.MESSAGE_TYPE_INBOX ->
                Telephony.Sms.Inbox.CONTENT_URI

            Telephony.Sms.MESSAGE_TYPE_SENT ->
                Telephony.Sms.Sent.CONTENT_URI

            Telephony.Sms.MESSAGE_TYPE_DRAFT ->
                Telephony.Sms.Draft.CONTENT_URI

            Telephony.Sms.MESSAGE_TYPE_OUTBOX ->
                Telephony.Sms.Outbox.CONTENT_URI

            Telephony.Sms.MESSAGE_TYPE_FAILED,
            Telephony.Sms.MESSAGE_TYPE_QUEUED ->
                Telephony.Sms.CONTENT_URI

            else -> Telephony.Sms.CONTENT_URI
        }
}
