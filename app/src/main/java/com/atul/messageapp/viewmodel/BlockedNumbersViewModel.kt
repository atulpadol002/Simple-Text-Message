package com.atul.messageapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.preferences.BlockedNumbersPreferences
import com.atul.messageapp.utils.getContactName
import com.atul.messageapp.receiver.SmsEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockedNumbersViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val blockedNumbersPreferences =
        BlockedNumbersPreferences(application)

    private val _blockedNumbers =
        MutableStateFlow<List<String>>(
            emptyList()
        )

    val blockedNumbers: StateFlow<List<String>> =
        _blockedNumbers.asStateFlow()

    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    init {
        loadBlockedNumbers()
    }

    fun loadBlockedNumbers() {

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val numbers = blockedNumbersPreferences.getBlockedNumbers().sorted()
                numbers to numbers.associateWith { getContactName(getApplication(), it) }
            }
            _blockedNumbers.value = result.first
            _contactNames.value = result.second
        }
    }

    fun blockNumber(
        phoneNumber: String
    ): Boolean {

        val blocked =
            blockedNumbersPreferences
                .blockNumber(phoneNumber)

        if (blocked) {
            loadBlockedNumbers()
        }

        return blocked
    }

    fun unblockNumber(phoneNumber: String) {
        viewModelScope.launch {
            val unblocked = withContext(Dispatchers.IO) {
                blockedNumbersPreferences.unblockNumber(phoneNumber)
            }
            if (unblocked) {
                _blockedNumbers.value = _blockedNumbers.value.filterNot { it == phoneNumber }
                _contactNames.value = _contactNames.value - phoneNumber
                SmsEventBus.notifyConversationUnblocked()
                loadBlockedNumbers()
            }
        }
    }

    fun isNumberBlocked(
        phoneNumber: String
    ): Boolean {

        return blockedNumbersPreferences
            .isNumberBlocked(phoneNumber)
    }
}
