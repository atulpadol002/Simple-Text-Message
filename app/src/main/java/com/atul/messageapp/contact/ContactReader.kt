package com.atul.messageapp.contact

import android.content.Context
import android.provider.ContactsContract
import com.atul.messageapp.data.model.Contact

class ContactReader(
    private val context: Context
) {

    fun getContacts(): List<Contact> {
        val contacts = ArrayList<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {

            val nameIndex = it.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            val phoneIndex = it.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val numbers = HashSet<String>()

            while (it.moveToNext()) {

                val phone = it.getString(phoneIndex)
                    ?.replace(" ", "")
                    ?.replace("-", "")
                    ?: ""

                if (numbers.add(phone)) {

                    contacts.add(
                        Contact(
                            name = it.getString(nameIndex) ?: "Unknown",
                            phoneNumber = phone
                        )
                    )

                }

            }

        }

        return contacts

    }

}