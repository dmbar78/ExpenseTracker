package com.example.expensetracker.sharedimport

import java.time.ZoneId
import java.util.Locale

internal fun shouldTryEnhancedOcr(
    text: String,
    locale: Locale,
    nowMillis: Long,
    zoneId: ZoneId
): Boolean {
    val parsed = ReceiptTextParser.parse(text, locale, nowMillis, zoneId)
    return parsed.amount == null || ReceiptTextParser.parseDate(text, locale, zoneId) == null
}

internal fun selectBetterOcrText(
    original: String,
    enhanced: String,
    locale: Locale,
    nowMillis: Long,
    zoneId: ZoneId
): String {
    fun score(text: String): Int {
        val parsed = ReceiptTextParser.parse(text, locale, nowMillis, zoneId)
        return (if (parsed.amount != null) 2 else 0) +
            (if (ReceiptTextParser.parseDate(text, locale, zoneId) != null) 1 else 0)
    }

    return if (score(enhanced) > score(original)) enhanced else original
}