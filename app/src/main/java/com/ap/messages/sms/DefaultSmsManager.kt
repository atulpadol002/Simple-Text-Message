package com.ap.messages.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * Wraps the official Android APIs for reading and requesting the default
 * SMS app role, abstracting the version split introduced in Android 10 (Q):
 *
 *  - API 29+     : android.app.role.RoleManager - the current, recommended API
 *  - API 26-28   : the legacy Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT intent
 *    (this app's minSdk is 26, so this path is still required)
 *
 * This class only reads state and builds Intents - it never calls
 * startActivity()/startActivityForResult() itself. Launching the returned
 * Intent (and observing whether the user granted or cancelled it) is the
 * caller's responsibility via an ActivityResultLauncher, which is the only
 * Android-supported way to get that result back.
 */
class DefaultSmsManager(
    private val context: Context
) {

    fun isDefaultSmsApp(): Boolean {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val roleManager =
                context.getSystemService(RoleManager::class.java)

            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) ?: false

        } else {

            Telephony.Sms.getDefaultSmsPackage(context) ==
                    context.packageName

        }

    }

    /**
     * Whether the SMS role can be requested on this device at all. Mainly
     * relevant on API 29+ devices without telephony hardware, where the
     * role may not be offered.
     */
    fun isRoleAvailable(): Boolean {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val roleManager =
                context.getSystemService(RoleManager::class.java)

            roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) ?: false

        } else {

            // Legacy path has no equivalent availability check - it's
            // effectively always available on a device with telephony.
            true

        }

    }

    /**
     * Builds (but does not launch) the platform intent for requesting the
     * default SMS role for this app.
     *
     * Returns null when a request is unnecessary or not possible right now:
     * the app already holds the role, or the role isn't available on this
     * device. Callers should treat a null result as "nothing to do" rather
     * than an error.
     */
    fun createRequestRoleIntent(): Intent? {

        if (isDefaultSmsApp() || !isRoleAvailable()) {
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val roleManager =
                context.getSystemService(RoleManager::class.java)

            roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)

        } else {

            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {

                putExtra(
                    Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                    context.packageName
                )

            }

        }

    }

}