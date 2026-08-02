package com.atul.messageapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils

data class ContactPresentation(val displayName: String, val photo: Bitmap?)

class ContactPresentationResolver(context: Context) {
    private val appContext = context.applicationContext
    private val cache = mutableMapOf<String, ContactPresentation>()

    fun resolve(address: String): ContactPresentation {
        val key = PhoneNumberUtils.normalizeNumber(address).ifBlank { address.trim() }
        synchronized(cache) { cache[key]?.let { return it } }
        var name = address
        var photo: Bitmap? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            appContext.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI
                ), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)?.takeIf(String::isNotBlank) ?: address
                    cursor.getString(1)?.let { photoUri ->
                        appContext.contentResolver.openInputStream(Uri.parse(photoUri))?.use {
                            photo = BitmapFactory.decodeStream(it)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            photo = null
        }
        return ContactPresentation(name, photo).also { synchronized(cache) { cache[key] = it } }
    }
}
