package com.ap.simpletextmessage.observer

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper

class SmsContentObserver(
    private val onSmsChanged: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onSmsChanged()
    }
}
