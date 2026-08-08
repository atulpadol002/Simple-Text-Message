package com.ap.messages.utils

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ContactUtils {

    suspend fun findContactUri(
        context: Context,
        phoneNumber: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(
                    ContactsContract.PhoneLookup.CONTACT_ID,
                    ContactsContract.PhoneLookup.LOOKUP_KEY
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val contactId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.CONTACT_ID)
                )
                val lookupKey = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.LOOKUP_KEY)
                )
                ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
            }
        } catch (exception: CancellationException) {
            throw exception
        }
    }

    fun getContactName(
        context: Context,
        phoneNumber: String
    ): String {

        if (phoneNumber.isBlank()) {
            return ""
        }

        val lookupUri =
            Uri.withAppendedPath(
                ContactsContract
                    .PhoneLookup
                    .CONTENT_FILTER_URI,
                Uri.encode(
                    phoneNumber
                )
            )

        return try {

            context.contentResolver.query(
                lookupUri,
                arrayOf(
                    ContactsContract
                        .PhoneLookup
                        .DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {

                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            ContactsContract
                                .PhoneLookup
                                .DISPLAY_NAME
                        )
                    )?.takeIf {
                        it.isNotBlank()
                    } ?: phoneNumber

                } else {

                    phoneNumber
                }

            } ?: phoneNumber

        } catch (
            exception: SecurityException
        ) {

            exception.printStackTrace()
            phoneNumber

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()
            phoneNumber
        }
    }
}
