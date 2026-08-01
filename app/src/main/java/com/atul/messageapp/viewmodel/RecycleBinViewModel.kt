package com.atul.messageapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.model.DeletedConversation
import com.atul.messageapp.data.repository.RecycleBinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecycleBinViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val recycleBinRepository =
        RecycleBinRepository(application)

    private val _deletedConversations =
        MutableStateFlow<List<DeletedConversation>>(emptyList())

    val deletedConversations: StateFlow<List<DeletedConversation>> =
        _deletedConversations.asStateFlow()

    private val _isLoading = MutableStateFlow(true)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    init {
        loadDeletedConversations()
    }

    fun loadDeletedConversations() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                _deletedConversations.value =
                    withContext(Dispatchers.IO) {
                        recycleBinRepository
                            .getDeletedConversations()
                            .sortedByDescending { conversation ->
                                conversation.deletedAt
                            }
                    }
            } catch (exception: Exception) {
                exception.printStackTrace()
                _deletedConversations.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
