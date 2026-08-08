package com.ap.messages.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ap.messages.data.preferences.BlockedNumbersPreferences
import com.ap.messages.utils.getContactName
import com.ap.messages.receiver.SmsEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

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

    fun blockNumberAsync(phoneNumber: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val blocked = blockedNumbersPreferences.blockNumber(phoneNumber)
                blocked to getContactName(getApplication(), phoneNumber)
            }
            val blocked = result.first
            if (blocked) {
                val normalized = blockedNumbersPreferences.normalize(phoneNumber)
                _blockedNumbers.value = (_blockedNumbers.value + normalized).distinct().sorted()
                _contactNames.value = _contactNames.value + (normalized to result.second)
                SmsEventBus.notifyConversationBlocked(normalized)
            }
            onResult(blocked)
        }
    }

    fun setVisibleSelection(ids: Set<String>, selected: Boolean) {
        _selectedNumbers.value = if (selected) _selectedNumbers.value + ids else _selectedNumbers.value - ids
    }

    private val _selectedNumbers = MutableStateFlow<Set<String>>(emptySet())
    val selectedNumbers: StateFlow<Set<String>> = _selectedNumbers.asStateFlow()
    fun toggleSelection(number: String) {
        _selectedNumbers.value = _selectedNumbers.value.toMutableSet().apply { if (!add(number)) remove(number) }
    }
    fun clearSelection() { _selectedNumbers.value = emptySet() }
    fun unblockSelected() {
        val selected = _selectedNumbers.value
        if (selected.isEmpty()) return
        _blockedNumbers.value = _blockedNumbers.value.filterNot { it in selected }
        _contactNames.value = _contactNames.value - selected
        _selectedNumbers.value = emptySet()
        viewModelScope.launch {
            try {
                val changed = withContext(Dispatchers.IO) { selected.count { blockedNumbersPreferences.unblockNumber(it) } }
                if (changed > 0) SmsEventBus.notifyConversationUnblocked()
            } catch (e: CancellationException) { throw e }
        }
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
