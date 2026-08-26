package com.ap.simpletextmessage.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

fun getContactName(
    context: Context,
    phoneNumber: String
): String {

    if (
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return phoneNumber
    }

    val uri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(phoneNumber)
    )

    return try {

        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {

                val nameIndex = cursor.getColumnIndex(
                    ContactsContract.PhoneLookup.DISPLAY_NAME
                )

                if (nameIndex >= 0) {
                    cursor.getString(nameIndex) ?: phoneNumber
                } else {
                    phoneNumber
                }

            } else {
                phoneNumber
            }

        } ?: phoneNumber

    } catch (exception: Exception) {
        phoneNumber
    }
}