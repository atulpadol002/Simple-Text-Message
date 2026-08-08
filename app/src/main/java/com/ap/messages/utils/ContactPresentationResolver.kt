package com.ap.messages.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.CancellationException

data class ContactPresentation(val displayName: String, val photo: Bitmap?)

class ContactPresentationResolver(context: Context) {
    private val appContext = context.applicationContext

    fun getCached(address: String): ContactPresentation? {
        val key = cacheKey(address)
        return synchronized(cacheLock) { presentationCache[key] }
    }

    fun resolve(address: String): ContactPresentation {
        val key = cacheKey(address)
        synchronized(cacheLock) { presentationCache[key]?.let { return it } }
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
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            photo = null
        }
        return ContactPresentation(name, photo).also {
            synchronized(cacheLock) { presentationCache[key] = it }
        }
    }

    companion object {
        private const val MAX_CACHE_ENTRIES = 128
        private val cacheLock = Any()
        private val presentationCache = object : LinkedHashMap<String, ContactPresentation>(
            MAX_CACHE_ENTRIES,
            0.75f,
            true
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ContactPresentation>?
            ): Boolean = size > MAX_CACHE_ENTRIES
        }

        /**
         * Keep the exact provider address in the cache identity. A normalized number alone is not
         * unique: extensions, service numbers and distinct sender IDs can otherwise collide.
         */
        fun cacheKey(address: String): String {
            val trimmed = address.trim()
            val kind = if (trimmed.any(Char::isLetter)) "sender" else "number"
            return "$kind:$trimmed"
        }
    }
}
