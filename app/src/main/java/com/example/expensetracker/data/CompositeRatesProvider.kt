package com.example.expensetracker.data

import android.util.Log
import java.math.BigDecimal

/**
 * Composite provider that delegates to a list of providers in order.
 * Implements a fallback chain mechanism.
 */
class CompositeRatesProvider(
    private val providers: List<RatesProvider>
) : RatesProvider {

    companion object {
        private const val TAG = "CompositeRatesProvider"
    }

    override suspend fun fetchRate(baseCurrency: String, quoteCurrency: String, date: Long): BigDecimal? {
        for ((index, provider) in providers.withIndex()) {
            try {
                val rate = provider.fetchRate(baseCurrency, quoteCurrency, date)
                if (rate != null) {
                    if (index > 0) {
                        // used fallback
                    }
                    return rate
                }
            } catch (e: Exception) {
                // Provider failed
            }
        }
        return null
    }

    override suspend fun fetchRates(baseCurrency: String, date: Long): Map<String, BigDecimal> {
        for ((index, provider) in providers.withIndex()) {
            try {
                val rates = provider.fetchRates(baseCurrency, date)
                if (rates.isNotEmpty()) {
                    if (index > 0) {
                        // used fallback
                    }
                    return rates
                }
            } catch (e: Exception) {
                // Provider failed
            }
        }
        return emptyMap()
    }
}
