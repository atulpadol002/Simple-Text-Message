package com.ap.messages.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ap.messages.contact.ContactRepository
import com.ap.messages.data.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ContactUiState {
    data object InitialLoading : ContactUiState
    data class Content(val contacts: List<Contact>) : ContactUiState
    data object Empty : ContactUiState
    data object Error : ContactUiState
}

class ContactViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = ContactRepository(application)

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    private val _uiState = MutableStateFlow<ContactUiState>(ContactUiState.InitialLoading)
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        loadContacts()
    }

    fun loadContacts() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        _uiState.value = ContactUiState.InitialLoading
        loadJob = viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    repository.getContacts()
                }
                currentCoroutineContext().ensureActive()
                if (generation != loadGeneration || loadJob !== currentCoroutineContext()[Job]) {
                    return@launch
                }
                _contacts.value = list
                _uiState.value = if (list.isEmpty()) ContactUiState.Empty else ContactUiState.Content(list)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (generation == loadGeneration && loadJob === currentCoroutineContext()[Job]) {
                    _uiState.value = ContactUiState.Error
                }
            }
        }
    }
}
