package com.atul.messageapp.utils

import android.telephony.SmsManager

object SmsHelper {

    fun sendSms(
        phoneNumber: String,
        message: String
    ): Boolean {

        return try {

            val smsManager = SmsManager.getDefault()

            smsManager.sendTextMessage(
                phoneNumber,
                null,
                message,
                null,
                null
            )

            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}