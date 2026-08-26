package com.ap.simpletextmessage.contact

import android.content.Context
import com.ap.simpletextmessage.data.model.Contact

class ContactRepository(
    context: Context
) {

    private val reader = ContactReader(context)

    fun getContacts(): List<Contact> {
        return reader.getContacts()
    }

}