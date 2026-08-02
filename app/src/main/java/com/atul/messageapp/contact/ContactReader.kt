package com.atul.messageapp.contact

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.ContextCompat
import com.atul.messageapp.data.model.Contact

class ContactReader(
    private val context: Context
) {

    fun getContacts(): List<Contact> {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val contacts = ArrayList<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        return try {

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
                val photoIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                val numbers = HashSet<String>()

                while (it.moveToNext()) {

                    val phone = it.getString(phoneIndex)
                        ?.replace(" ", "")
                        ?.replace("-", "")
                        ?: ""

                    if (numbers.add(phone)) {

                        val photo = try {
                            it.getString(photoIndex)?.let { uri ->
                                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                                    BitmapFactory.decodeStream(stream)
                                }
                            }
                        } catch (_: Exception) { null }
                        contacts.add(
                            Contact(
                                name = it.getString(nameIndex) ?: "Unknown",
                                phoneNumber = phone,
                                photo = photo
                            )
                        )

                    }

                }

            }

            contacts

        } catch (exception: RuntimeException) {

            emptyList()
        }

    }

}
