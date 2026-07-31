package com.atul.messageapp.utils

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactUtils {

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