package com.atul.messageapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.model.SmsMessage
import com.atul.messageapp.data.preferences.StarredMessagesPreferences
import com.atul.messageapp.data.repository.SmsRepository
import com.atul.messageapp.utils.getContactName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StarredMessagesViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = StarredMessagesPreferences(application)
    private val repository = SmsRepository(application)
    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()
    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private var loadJob: Job? = null
    private var reloadPending = false
    private var stateVersion = 0L

    init { load() }

    fun load() {
        if (loadJob?.isActive == true) {
            reloadPending = true
            return
        }
        val initialLoad = _messages.value.isEmpty() && _isLoading.value
        val version = stateVersion
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val starredIds = preferences.getStarredMessageIds()
                val messages = repository.getMessagesByIds(starredIds)
                val foundIds = messages.mapTo(mutableSetOf()) { it.id }
                preferences.removeStarredMessageIds(starredIds - foundIds)
                val names = messages.map { it.address }.distinct()
                    .associateWith { getContactName(getApplication(), it) }
                messages to names
            }
            if (version == stateVersion) {
                _messages.value = result.first
                _contactNames.value = result.second
            } else {
                reloadPending = true
            }
            if (initialLoad) _isLoading.value = false
            loadJob = null
            if (reloadPending) {
                reloadPending = false
                load()
            }
        }
    }

    fun unstar(messageId: Long) {
        stateVersion++
        _messages.value = _messages.value.filterNot { it.id == messageId }
        viewModelScope.launch(Dispatchers.IO) { preferences.unstarMessage(messageId) }
    }
}
