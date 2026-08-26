package com.ap.simpletextmessage.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_ADDRESS
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_BODY
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_CACHED_DISPLAY_NAME
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_DATE
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_DELETED_AT
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_LOCAL_MESSAGE_ID
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_ORIGINAL_MESSAGE_ID
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_ORIGINAL_THREAD_ID
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_READ
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_RECYCLE_BIN_ID
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_RESTORED
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_SEEN
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_SENT_DATE
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_SERVICE_CENTER
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_STATUS
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_SUBSCRIPTION_ID
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.COLUMN_TYPE
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.TABLE_DELETED_CONVERSATIONS
import com.ap.simpletextmessage.data.local.RecycleBinDatabaseHelper.Companion.TABLE_DELETED_MESSAGES
import com.ap.simpletextmessage.data.model.DeletedConversation
import com.ap.simpletextmessage.data.model.DeletedMessage

class RecycleBinRepository(
    context: Context
) {

    private val databaseHelper =
        RecycleBinDatabaseHelper(context)

    fun saveConversationSnapshot(
        conversation: DeletedConversation,
        messages: List<DeletedMessage>
    ): Boolean = saveConversationSnapshotAndGetId(
        conversation = conversation,
        messages = messages
    ) != null

    fun saveConversationSnapshotAndGetId(
        conversation: DeletedConversation,
        messages: List<DeletedMessage>
    ): Long? {
        if (
            messages.isEmpty() ||
            messages.any { message ->
                message.originalThreadId !=
                        conversation.originalThreadId
            }
        ) {
            return null
        }

        val database = databaseHelper.writableDatabase

        database.beginTransaction()

        return try {
            val recycleBinId = database.insertWithOnConflict(
                TABLE_DELETED_CONVERSATIONS,
                null,
                conversation.toContentValues(),
                SQLiteDatabase.CONFLICT_IGNORE
            )

            if (recycleBinId == -1L) {
                null
            } else {
                messages.forEach { message ->
                    val insertedId = database.insertOrThrow(
                        TABLE_DELETED_MESSAGES,
                        null,
                        message.toContentValues(recycleBinId)
                    )

                    check(insertedId != -1L)
                }

                database.setTransactionSuccessful()
                recycleBinId
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        } finally {
            database.endTransaction()
        }
    }

    fun getDeletedConversations():
            List<DeletedConversation> {
        val database = databaseHelper.readableDatabase

        return database.query(
            TABLE_DELETED_CONVERSATIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_DELETED_AT DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toDeletedConversation())
                }
            }
        }
    }

    fun getDeletedMessages(
        recycleBinId: Long
    ): List<DeletedMessage> {
        val database = databaseHelper.readableDatabase

        return database.query(
            TABLE_DELETED_MESSAGES,
            null,
            "$COLUMN_RECYCLE_BIN_ID=?",
            arrayOf(recycleBinId.toString()),
            null,
            null,
            "$COLUMN_DATE ASC, $COLUMN_LOCAL_MESSAGE_ID ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toDeletedMessage())
                }
            }
        }
    }

    fun deleteSnapshotPermanently(
        recycleBinId: Long
    ): Boolean {
        val database = databaseHelper.writableDatabase

        database.beginTransaction()

        return try {
            val deletedRows = database.delete(
                TABLE_DELETED_CONVERSATIONS,
                "$COLUMN_RECYCLE_BIN_ID=?",
                arrayOf(recycleBinId.toString())
            )

            if (deletedRows == 1) {
                database.setTransactionSuccessful()
                true
            } else {
                false
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            false
        } finally {
            database.endTransaction()
        }
    }

    fun markMessageRestored(
        localMessageId: Long
    ): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_RESTORED, 1)
        }

        return databaseHelper.writableDatabase.update(
            TABLE_DELETED_MESSAGES,
            values,
            "$COLUMN_LOCAL_MESSAGE_ID=?",
            arrayOf(localMessageId.toString())
        ) == 1
    }

    fun deleteSnapshotIfRestoreComplete(
        recycleBinId: Long
    ): Boolean {
        val database = databaseHelper.writableDatabase

        database.beginTransaction()

        return try {
            val remainingMessages = database.rawQuery(
                """
                SELECT COUNT(*)
                FROM $TABLE_DELETED_MESSAGES
                WHERE $COLUMN_RECYCLE_BIN_ID=?
                    AND $COLUMN_RESTORED=0
                """.trimIndent(),
                arrayOf(recycleBinId.toString())
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }

            if (remainingMessages != 0L) {
                false
            } else {
                val deletedRows = database.delete(
                    TABLE_DELETED_CONVERSATIONS,
                    "$COLUMN_RECYCLE_BIN_ID=?",
                    arrayOf(recycleBinId.toString())
                )

                if (deletedRows == 1) {
                    database.setTransactionSuccessful()
                    true
                } else {
                    false
                }
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            false
        } finally {
            database.endTransaction()
        }
    }

    private fun DeletedConversation.toContentValues() =
        ContentValues().apply {
            put(COLUMN_ORIGINAL_THREAD_ID, originalThreadId)
            put(COLUMN_ADDRESS, address)
            put(COLUMN_CACHED_DISPLAY_NAME, cachedDisplayName)
            put(COLUMN_DELETED_AT, deletedAt)
        }

    private fun DeletedMessage.toContentValues(
        parentRecycleBinId: Long
    ) = ContentValues().apply {
        put(COLUMN_RECYCLE_BIN_ID, parentRecycleBinId)
        put(COLUMN_ORIGINAL_MESSAGE_ID, originalMessageId)
        put(COLUMN_ORIGINAL_THREAD_ID, originalThreadId)
        put(COLUMN_ADDRESS, address)
        put(COLUMN_BODY, body)
        put(COLUMN_DATE, date)
        put(COLUMN_SENT_DATE, sentDate)
        put(COLUMN_TYPE, type)
        put(COLUMN_READ, read.toDatabaseInt())
        put(COLUMN_SEEN, seen.toDatabaseInt())
        put(COLUMN_STATUS, status)
        put(COLUMN_SERVICE_CENTER, serviceCenter)
        put(COLUMN_SUBSCRIPTION_ID, subscriptionId)
        put(COLUMN_RESTORED, restored.toDatabaseInt())
    }

    private fun Cursor.toDeletedConversation() =
        DeletedConversation(
            recycleBinId = getLong(column(COLUMN_RECYCLE_BIN_ID)),
            originalThreadId = getLong(column(COLUMN_ORIGINAL_THREAD_ID)),
            address = getString(column(COLUMN_ADDRESS)),
            cachedDisplayName = getNullableString(
                COLUMN_CACHED_DISPLAY_NAME
            ),
            deletedAt = getLong(column(COLUMN_DELETED_AT))
        )

    private fun Cursor.toDeletedMessage() =
        DeletedMessage(
            localMessageId = getLong(column(COLUMN_LOCAL_MESSAGE_ID)),
            recycleBinId = getLong(column(COLUMN_RECYCLE_BIN_ID)),
            originalMessageId = getLong(column(COLUMN_ORIGINAL_MESSAGE_ID)),
            originalThreadId = getLong(column(COLUMN_ORIGINAL_THREAD_ID)),
            address = getString(column(COLUMN_ADDRESS)),
            body = getString(column(COLUMN_BODY)),
            date = getLong(column(COLUMN_DATE)),
            sentDate = getNullableLong(COLUMN_SENT_DATE),
            type = getInt(column(COLUMN_TYPE)),
            read = getInt(column(COLUMN_READ)) == 1,
            seen = getInt(column(COLUMN_SEEN)) == 1,
            status = getNullableInt(COLUMN_STATUS),
            serviceCenter = getNullableString(COLUMN_SERVICE_CENTER),
            subscriptionId = getNullableInt(COLUMN_SUBSCRIPTION_ID),
            restored = getInt(column(COLUMN_RESTORED)) == 1
        )

    private fun Cursor.column(name: String): Int =
        getColumnIndexOrThrow(name)

    private fun Cursor.getNullableString(
        name: String
    ): String? {
        val index = column(name)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getNullableLong(
        name: String
    ): Long? {
        val index = column(name)
        return if (isNull(index)) null else getLong(index)
    }

    private fun Cursor.getNullableInt(
        name: String
    ): Int? {
        val index = column(name)
        return if (isNull(index)) null else getInt(index)
    }

    private fun Boolean.toDatabaseInt(): Int =
        if (this) 1 else 0
}
