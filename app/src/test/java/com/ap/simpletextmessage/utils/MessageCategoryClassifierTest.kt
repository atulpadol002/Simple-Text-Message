package com.ap.simpletextmessage.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCategoryClassifierTest {
    @Test
    fun otpTakesPrecedenceOverBankingLanguage() {
        assertEquals(
            MessageCategory.OTP,
            classifyMessage("VK-HDFCBK", "OTP 482193 is your verification code for this transaction")
        )
    }

    @Test
    fun completedTransactionsTakePrecedenceOverOfferWords() {
        assertEquals(
            MessageCategory.TRANSACTIONS,
            classifyMessage("AD-AXISBK-S", "INR 750 was debited from your account via UPI")
        )
        assertEquals(
            MessageCategory.TRANSACTIONS,
            classifyMessage("VM-PAYTM", "Cashback of INR 50 was credited to your wallet")
        )
    }

    @Test
    fun transactionMarketingIsNotMistakenForACompletedTransaction() {
        assertEquals(
            MessageCategory.OFFERS,
            classifyMessage("AD-HDFCBK", "Earn cashback on every card transaction. Apply now")
        )
    }

    @Test
    fun simOperatorPromotionsAreOffers() {
        assertEquals(
            MessageCategory.OFFERS,
            classifyMessage("VZ-AIRTEL", "Special offer: recharge now with ₹299 for unlimited data")
        )
        assertEquals(
            MessageCategory.OFFERS,
            classifyMessage("VM-JIO", "Get a ₹299 recharge offer with 28 days validity and 2GB/day")
        )
    }

    @Test
    fun investmentAndBrokerMessagesAreNotPersonal() {
        listOf(
            "BZ-ZERODHA" to "Market alert: NIFTY crossed 25,000. Review your portfolio.",
            "UPSTOX" to "Open a demat account and start trading today.",
            "BROKER" to "Investment opportunity in selected stocks. Invest now."
        ).forEach { (sender, body) ->
            assertEquals(MessageCategory.OFFERS, classifyMessage(sender, body))
        }
    }

    @Test
    fun votingCompanyAndOtherAutomatedMessagesAreNotPersonal() {
        listOf(
            "VM-COMPANY" to "Voting is open for the shareholder resolution.",
            "JIOINFO" to "Your operator service settings were updated.",
            "56789" to "This is an automated service alert."
        ).forEach { (sender, body) ->
            assertEquals(MessageCategory.OFFERS, classifyMessage(sender, body))
        }
    }

    @Test
    fun ordinaryConversationIsPersonal() {
        assertEquals(
            MessageCategory.PERSONAL,
            classifyMessage("+919876543210", "Are we still meeting this evening?")
        )
        assertEquals(
            MessageCategory.PERSONAL,
            classifyMessage("+919876543210", "I started investing last month. Call me later.")
        )
    }
}
