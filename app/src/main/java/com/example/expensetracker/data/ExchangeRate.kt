package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.Index
import java.math.BigDecimal

/**
 * Stores exchange rates for currency pairs by date.
 * Rate represents: 1 baseCurrency = rate quoteCurrency
 * 
 * Example: if baseCurrencyCode="EUR", quoteCurrencyCode="USD", rate=1.10
 * means 1 EUR = 1.10 USD
 * 
 * For same-currency pairs (X→X), rate should always be 1.
 */
@Entity(
    tableName = "exchange_rates",
    primaryKeys = ["date", "baseCurrencyCode", "quoteCurrencyCode"],
    indices = [
        Index(value = ["baseCurrencyCode", "quoteCurrencyCode", "date"])
    ]
)
data class ExchangeRate(
    /** Date in millis, normalized to start of day (00:00:00) */
    val date: Long,
    /** The base currency code (from) */
    val baseCurrencyCode: String,
    /** The quote currency code (to) */
    val quoteCurrencyCode: String,
    /** Exchange rate: 1 base = rate quote */
    val rate: BigDecimal
)
