package com.atul.messageapp.contact

import android.content.Context
import android.provider.ContactsContract

class ContactNameResolver(
    private val context: Context
) {

    fun getContactName(phoneNumber: String): String {

        val normalizedNumber = phoneNumber
            .replace(" ", "")
            .replace("-", "")

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )

        cursor?.use {

            val nameIndex = it.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            val numberIndex = it.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            while (it.moveToNext()) {

                val contactNumber = it
                    .getString(numberIndex)
                    ?.replace(" ", "")
                    ?.replace("-", "")
                    ?: ""

                if (
                    contactNumber.endsWith(normalizedNumber.takeLast(10))
                ) {

                    return it.getString(nameIndex) ?: phoneNumber

                }

            }

        }

        return phoneNumber

    }

}