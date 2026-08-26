package com.ap.simpletextmessage.utils

enum class MessageCategory {
    ALL,
    PERSONAL,
    OTP,
    TRANSACTIONS,
    OFFERS
}

private val otpTerms = Regex(
    "\\b(otp|one[ -]?time (password|code)|verification code|verify|authentication code|" +
        "security code|login code|passcode)\\b",
    RegexOption.IGNORE_CASE
)
private val otpNumber = Regex("\\b\\d{4,8}\\b")

private val completedTransactionTerms = Regex(
    "\\b(debited|credited|spent|received|paid|sent|withdrawn|withdrawal|deposited|deposit|" +
        "transferred|payment (successful|failed|received|completed)|transaction (successful|failed)|" +
        "refund (initiated|processed|credited|received))\\b",
    RegexOption.IGNORE_CASE
)
private val financialStatusTerms = Regex(
    "\\b(available balance|current balance|account balance|statement generated|amount due|" +
        "minimum amount due|bill due|payment due)\\b",
    RegexOption.IGNORE_CASE
)
private val transactionReference = Regex(
    "\\b(upi|txn|transaction)\\s*(id|ref(?:erence)?|no\\.?)\\b",
    RegexOption.IGNORE_CASE
)

private val offerTerms = Regex(
    "\\b(recharge|data ?pack|data plan|plan|validity|offer|sale|discount|deal|coupon|promo|" +
        "promotional|cashback|save|limited time|shop now|buy now|free|unlimited|special offer|" +
        "subscribe|renew|upgrade|% off)\\b",
    RegexOption.IGNORE_CASE
)
private val commercialServiceTerms = Regex(
    "\\b(stock market|stocks?|shares?|investment|investing|broker(?:age)?|trading|trade alert|" +
        "demat|mutual funds?|portfolio|nifty|sensex|prepaid|postpaid|telecom|operator service|" +
        "vote|voting|shareholder|company alert)\\b",
    RegexOption.IGNORE_CASE
)
private val commercialCallToAction = Regex(
    "\\b(open (?:a |your )?(?:demat|trading|investment) account|start trading|invest now|" +
        "register now|download (?:the )?app|click (?:here|the link)|visit (?:us|our|www))\\b",
    RegexOption.IGNORE_CASE
)
private val operatorSender = Regex(
    "(^|[-_])(airtel|jio|vi|vodafone|idea|bsnl|mtnl|telecom|recharge|data)([-_]|$)|" +
        "^(v[zm]|cp)-[a-z0-9]+",
    RegexOption.IGNORE_CASE
)

fun classifyMessage(address: String, body: String): MessageCategory {
    val text = body.trim()
    val sender = address.trim()

    if (otpTerms.containsMatchIn(text) && otpNumber.containsMatchIn(text)) {
        return MessageCategory.OTP
    }

    // Only completed monetary events and account-status notices win transaction precedence.
    // Marketing that merely mentions a transaction or a bank therefore remains an offer.
    if (
        completedTransactionTerms.containsMatchIn(text) ||
        financialStatusTerms.containsMatchIn(text) ||
        transactionReference.containsMatchIn(text)
    ) {
        return MessageCategory.TRANSACTIONS
    }

    val automatedSender = !isReplyCapableAddress(sender)
    if (
        offerTerms.containsMatchIn(text) ||
        commercialCallToAction.containsMatchIn(text) ||
        operatorSender.containsMatchIn(sender) ||
        (automatedSender && commercialServiceTerms.containsMatchIn(text)) ||
        automatedSender
    ) {
        return MessageCategory.OFFERS
    }

    return MessageCategory.PERSONAL
}
