package com.ap.messages.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RecycleBinDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onConfigure(
        database: SQLiteDatabase
    ) {
        super.onConfigure(database)
        database.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(
        database: SQLiteDatabase
    ) {
        database.execSQL(CREATE_DELETED_CONVERSATIONS)
        database.execSQL(CREATE_DELETED_MESSAGES)
        database.execSQL(CREATE_MESSAGES_RECYCLE_BIN_INDEX)
    }

    override fun onUpgrade(
        database: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Version 1 is the initial schema. Future versions must migrate data
        // instead of dropping recycle-bin snapshots.
    }

    companion object {

        const val TABLE_DELETED_CONVERSATIONS =
            "deleted_conversations"

        const val COLUMN_RECYCLE_BIN_ID =
            "recycle_bin_id"

        const val COLUMN_ORIGINAL_THREAD_ID =
            "original_thread_id"

        const val COLUMN_ADDRESS =
            "address"

        const val COLUMN_CACHED_DISPLAY_NAME =
            "cached_display_name"

        const val COLUMN_DELETED_AT =
            "deleted_at"

        const val TABLE_DELETED_MESSAGES =
            "deleted_messages"

        const val COLUMN_LOCAL_MESSAGE_ID =
            "local_message_id"

        const val COLUMN_ORIGINAL_MESSAGE_ID =
            "original_message_id"

        const val COLUMN_BODY =
            "body"

        const val COLUMN_DATE =
            "date"

        const val COLUMN_SENT_DATE =
            "sent_date"

        const val COLUMN_TYPE =
            "type"

        const val COLUMN_READ =
            "is_read"

        const val COLUMN_SEEN =
            "seen"

        const val COLUMN_STATUS =
            "status"

        const val COLUMN_SERVICE_CENTER =
            "service_center"

        const val COLUMN_SUBSCRIPTION_ID =
            "subscription_id"

        const val COLUMN_RESTORED =
            "restored"

        private const val DATABASE_NAME =
            "recycle_bin.db"

        private const val DATABASE_VERSION = 1

        private const val CREATE_DELETED_CONVERSATIONS =
            """
            CREATE TABLE $TABLE_DELETED_CONVERSATIONS (
                $COLUMN_RECYCLE_BIN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_ORIGINAL_THREAD_ID INTEGER NOT NULL UNIQUE,
                $COLUMN_ADDRESS TEXT NOT NULL,
                $COLUMN_CACHED_DISPLAY_NAME TEXT,
                $COLUMN_DELETED_AT INTEGER NOT NULL
            )
            """

        private const val CREATE_DELETED_MESSAGES =
            """
            CREATE TABLE $TABLE_DELETED_MESSAGES (
                $COLUMN_LOCAL_MESSAGE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_RECYCLE_BIN_ID INTEGER NOT NULL,
                $COLUMN_ORIGINAL_MESSAGE_ID INTEGER NOT NULL,
                $COLUMN_ORIGINAL_THREAD_ID INTEGER NOT NULL,
                $COLUMN_ADDRESS TEXT NOT NULL,
                $COLUMN_BODY TEXT NOT NULL,
                $COLUMN_DATE INTEGER NOT NULL,
                $COLUMN_SENT_DATE INTEGER,
                $COLUMN_TYPE INTEGER NOT NULL,
                $COLUMN_READ INTEGER NOT NULL,
                $COLUMN_SEEN INTEGER NOT NULL,
                $COLUMN_STATUS INTEGER,
                $COLUMN_SERVICE_CENTER TEXT,
                $COLUMN_SUBSCRIPTION_ID INTEGER,
                $COLUMN_RESTORED INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY ($COLUMN_RECYCLE_BIN_ID)
                    REFERENCES $TABLE_DELETED_CONVERSATIONS($COLUMN_RECYCLE_BIN_ID)
                    ON DELETE CASCADE,
                UNIQUE ($COLUMN_RECYCLE_BIN_ID, $COLUMN_ORIGINAL_MESSAGE_ID)
            )
            """

        private const val CREATE_MESSAGES_RECYCLE_BIN_INDEX =
            """
            CREATE INDEX index_deleted_messages_recycle_bin_id
            ON $TABLE_DELETED_MESSAGES($COLUMN_RECYCLE_BIN_ID)
            """
    }
}
