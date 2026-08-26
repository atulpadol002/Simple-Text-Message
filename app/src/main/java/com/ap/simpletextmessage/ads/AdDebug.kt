package com.ap.simpletextmessage.ads

import android.util.Log
import com.ap.simpletextmessage.BuildConfig

internal object AdDebug {
    const val TAG = "AdDebug"

    inline fun log(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }
}
