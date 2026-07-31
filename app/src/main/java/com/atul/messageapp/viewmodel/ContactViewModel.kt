package com.atul.messageapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.contact.ContactRepository
import com.atul.messageapp.data.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = ContactRepository(application)

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    init {
        loadContacts()
    }

    fun loadContacts() {

        viewModelScope.launch {

            val list = withContext(Dispatchers.IO) {
                repository.getContacts()
            }

            _contacts.value = list

        }

    }

}