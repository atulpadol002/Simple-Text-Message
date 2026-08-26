package com.ap.simpletextmessage.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException

data class ContactPresentation(
    val displayName: String,
    val photo: Bitmap?,
    val lookupComplete: Boolean = true
)

class ContactPresentationResolver(context: Context) {
    private val appContext = context.applicationContext

    fun getCached(address: String): ContactPresentation? {
        val key = cacheKey(address)
        return synchronized(cacheLock) { presentationCache[key] }
    }

    fun resolve(address: String): ContactPresentation {
        val key = cacheKey(address)
        synchronized(cacheLock) { presentationCache[key]?.let { return it } }
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Home can be composed before the optional contacts permission dialog completes.
            // Do not make that temporary fallback sticky in the process-wide cache.
            return ContactPresentation(address, null, lookupComplete = false)
        }
        var name = address
        var photo: Bitmap? = null
        var lookupComplete = false
        try {
            val normalized = PhoneNumberUtils.normalizeNumber(address).ifBlank { address.trim() }
            val candidates = listOf(address, normalized).distinct()
            for (candidate in candidates) {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(candidate)
                )
                val found = appContext.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_URI
                    ), null, null, null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use false
                    name = cursor.getString(0)?.takeIf(String::isNotBlank) ?: address
                    cursor.getString(1)?.let { photoUri ->
                        photo = runCatching {
                            appContext.contentResolver.openInputStream(Uri.parse(photoUri))?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        }.getOrNull()
                    }
                    true
                } ?: false
                if (found) {
                    break
                }
            }
            lookupComplete = true
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            photo = null
        }
        return ContactPresentation(name, photo, lookupComplete).also { presentation ->
            if (presentation.lookupComplete) {
                synchronized(cacheLock) {
                    presentationCache[key] = presentation
                }
            }
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
