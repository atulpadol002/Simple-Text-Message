package com.ap.messages.contact

import android.content.Context
import com.ap.messages.data.model.Contact

class ContactRepository(
    context: Context
) {

    private val reader = ContactReader(context)

    fun getContacts(): List<Contact> {
        return reader.getContacts()
    }

}