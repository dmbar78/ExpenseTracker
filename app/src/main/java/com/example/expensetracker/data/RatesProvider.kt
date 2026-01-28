package com.example.expensetracker.data

import java.math.BigDecimal

/**
 * Interface for fetching exchange rates from an external source.
 * Implementations can be offline (placeholder), network-based (Retrofit), etc.
 */
interface RatesProvider {
    /**
     * Fetch the rate for a currency pair on a specific date.
     * Returns null if the rate cannot be obtained.
     */
    suspend fun fetchRate(baseCurrency: String, quoteCurrency: String, date: Long): BigDecimal?
    
    /**
     * Fetch multiple rates for a base currency on a specific date.
     * Returns a map of quoteCurrency -> rate.
     */
    suspend fun fetchRates(baseCurrency: String, date: Long): Map<String, BigDecimal>
}

/**
 * Offline placeholder provider that returns no rates.
 * To be replaced with a network-based provider later.
 */
class OfflineRatesProvider : RatesProvider {
    override suspend fun fetchRate(baseCurrency: String, quoteCurrency: String, date: Long): BigDecimal? {
        // Same currency always has rate 1
        return if (baseCurrency == quoteCurrency) BigDecimal.ONE else null
    }
    
    override suspend fun fetchRates(baseCurrency: String, date: Long): Map<String, BigDecimal> {
        // Only return the identity rate
        return mapOf(baseCurrency to BigDecimal.ONE)
    }
}

/**
 * Result of a rate lookup operation.
 */
sealed class RateResult {
    data class Success(val rate: BigDecimal) : RateResult()
    data object Missing : RateResult()
}
