package com.atul.messageapp.data.preferences

import android.content.Context

class BlockedNumbersPreferences(
    context: Context
) {

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

    fun getBlockedNumbers(): Set<String> {

        return preferences.getStringSet(
            KEY_BLOCKED_NUMBERS,
            emptySet()
        )?.toSet() ?: emptySet()
    }

    fun isNumberBlocked(
        phoneNumber: String
    ): Boolean {

        return getBlockedNumbers()
            .any { blockedNumber ->

                matchesBlockedValue(
                    blockedNumber,
                    phoneNumber
                )
            }
    }

    fun blockNumber(
        phoneNumber: String
    ): Boolean {

        val cleanedNumber = normalize(phoneNumber)

        if (cleanedNumber.isBlank()) {
            return false
        }

        val currentNumbers =
            getBlockedNumbers()
                .toMutableSet()

        val alreadyBlocked =
            currentNumbers.any {
                    blockedNumber ->

                matchesBlockedValue(
                    blockedNumber,
                    cleanedNumber
                )
            }

        if (alreadyBlocked) {
            return false
        }

        currentNumbers.add(
            cleanedNumber
        )

        return preferences
            .edit()
            .putStringSet(
                KEY_BLOCKED_NUMBERS,
                currentNumbers
            )
            .commit()
    }

    fun unblockNumber(
        phoneNumber: String
    ): Boolean {

        val currentNumbers =
            getBlockedNumbers()
                .toMutableSet()

        val removed =
            currentNumbers.removeAll {
                    blockedNumber ->

                matchesBlockedValue(
                    blockedNumber,
                    phoneNumber
                )
            }

        if (!removed) {
            return false
        }

        return preferences
            .edit()
            .putStringSet(
                KEY_BLOCKED_NUMBERS,
                currentNumbers
            )
            .commit()
    }

    fun normalize(
        phoneNumber: String
    ): String {
        val trimmed = phoneNumber.trim()
        if (trimmed.any(Char::isLetter)) return trimmed.uppercase()
        return trimmed.filter(Char::isDigit).takeLast(PHONE_NUMBER_MATCH_LENGTH)
    }

    private fun matchesBlockedValue(
        blockedValue: String,
        candidateValue: String
    ): Boolean {

        val blocked = normalize(blockedValue)
        val candidate = normalize(candidateValue)
        return blocked.isNotBlank() && blocked == candidate
    }

    companion object {

        private const val PREFERENCES_NAME =
            "blocked_numbers_preferences"

        private const val KEY_BLOCKED_NUMBERS =
            "blocked_numbers"

        private const val PHONE_NUMBER_MATCH_LENGTH =
            10
    }
}
