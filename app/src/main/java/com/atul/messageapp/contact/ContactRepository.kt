package com.atul.messageapp.contact

import android.content.Context
import com.atul.messageapp.data.model.Contact

class ContactRepository(
    context: Context
) {

    private val reader = ContactReader(context)

    fun getContacts(): List<Contact> {
        return reader.getContacts()
    }

}