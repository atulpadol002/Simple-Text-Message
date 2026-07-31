package com.atul.messageapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.atul.messageapp.data.preferences.BlockedNumbersPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    init {
        loadBlockedNumbers()
    }

    fun loadBlockedNumbers() {

        _blockedNumbers.value =
            blockedNumbersPreferences
                .getBlockedNumbers()
                .sorted()
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

    fun unblockNumber(
        phoneNumber: String
    ): Boolean {

        val unblocked =
            blockedNumbersPreferences
                .unblockNumber(phoneNumber)

        if (unblocked) {
            loadBlockedNumbers()
        }

        return unblocked
    }

    fun isNumberBlocked(
        phoneNumber: String
    ): Boolean {

        return blockedNumbersPreferences
            .isNumberBlocked(phoneNumber)
    }
}