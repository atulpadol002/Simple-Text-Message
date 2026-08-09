package com.ap.messages.utils

private const val MinimumPhoneDigits = 7
private const val MaximumPhoneDigits = 15

/**
 * Returns whether an SMS address looks like a user-dialable phone number.
 *
 * This is deliberately address-only: it performs no provider, contact, network, or message-body lookup.
 */
fun isReplyCapableAddress(address: String): Boolean {
    val value = address.trim()
    if (value.isEmpty()) return false

    var digitCount = 0
    var plusCount = 0
    val brackets = ArrayDeque<Char>()

    value.forEachIndexed { index, character ->
        when {
            character in '0'..'9' -> digitCount++
            character == '+' -> {
                plusCount++
                if (index != 0 || plusCount > 1) return false
            }
            character == ' ' || character == '-' -> Unit
            character == '(' || character == '[' -> brackets.addLast(character)
            character == ')' -> if (brackets.removeLastOrNull() != '(') return false
            character == ']' -> if (brackets.removeLastOrNull() != '[') return false
            else -> return false
        }
    }

    if (brackets.isNotEmpty()) return false
    if (digitCount !in MinimumPhoneDigits..MaximumPhoneDigits) return false

    val digits = value.filter { it in '0'..'9' }
    return digits.any { it != digits.first() }
}
