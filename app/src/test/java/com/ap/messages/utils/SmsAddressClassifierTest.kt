package com.ap.messages.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsAddressClassifierTest {

    @Test
    fun normalPhoneNumbersAreReplyCapable() {
        assertTrue(isReplyCapableAddress("+91 98765 43210"))
        assertTrue(isReplyCapableAddress("9876543210"))
        assertTrue(isReplyCapableAddress("+1-202-555-0123"))
        assertTrue(isReplyCapableAddress("(020) 7946 0958"))
        assertTrue(isReplyCapableAddress("+44 [20] 7946 0958"))
    }

    @Test
    fun senderIdsAndShortCodesAreNotReplyCapable() {
        listOf(
            "VM-ViCARE-S",
            "AD-AXISBK-S",
            "CP-blnkit-S",
            "VK-HDFCBK",
            "56789",
            "123456",
            "BANKOTP",
            "JIOINFO",
            ""
        ).forEach { address ->
            assertFalse("Expected '$address' to be non-replyable", isReplyCapableAddress(address))
        }
    }

    @Test
    fun malformedNumbersAreNotReplyCapable() {
        listOf(
            "+1+2025550123",
            "202.555.0123",
            "(2025550123",
            "0000000000",
            "1234567890123456"
        ).forEach { address ->
            assertFalse("Expected '$address' to be malformed", isReplyCapableAddress(address))
        }
    }
}
