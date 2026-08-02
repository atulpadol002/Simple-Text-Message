package com.atul.messageapp.data.model

import android.graphics.Bitmap

data class Contact(

    val name: String,

    val phoneNumber: String,
    val photo: Bitmap? = null

)
