package com.ap.messages.ads

import android.util.Log
import com.ap.messages.BuildConfig

internal object AdDebug {
    const val TAG = "AdDebug"

    inline fun log(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }
}
