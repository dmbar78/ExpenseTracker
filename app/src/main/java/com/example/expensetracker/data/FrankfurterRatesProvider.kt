package com.example.expensetracker.data

import android.util.Log
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * RatesProvider implementation that fetches rates from the Frankfurter API.
 * 
 * Uses EUR as the pivot currency to minimize API calls:
 * - Always fetches EUR→X rates from Frankfurter
 * - Derives any pair A→B using cross-rate formula: A→B = (EUR→B) / (EUR→A)
 * - Special cases: A==B → 1, EUR→X → rX, X→EUR → 1/rX
 * 
 * Dates are treated as local calendar days but formatted as YYYY-MM-DD for the API.
 */
class FrankfurterRatesProvider(
    private val api: FrankfurterApi = FrankfurterApi.create()
) : RatesProvider {
    
    companion object {
        private const val TAG = "FrankfurterRatesProvider"
        private const val PIVOT_CURRENCY = "EUR"
        private const val RATE_SCALE = 10
    }
    
    // In-memory cache for EUR→X rates by date string
    // Key: "YYYY-MM-DD", Value: Map of currency code to rate
    private val eurRatesCache = mutableMapOf<String, Map<String, BigDecimal>>()
    
    // Track dates where fetch failed (negative cache) to avoid retry storms
    private val failedDates = mutableSetOf<String>()
    
    /**
     * Fetch a single rate by deriving from EUR pivot rates.
     * Prefers cached EUR rates if available.
     */
    override suspend fun fetchRate(baseCurrency: String, quoteCurrency: String, date: Long): BigDecimal? {
        // Same currency is always 1
        if (baseCurrency == quoteCurrency) {
            return BigDecimal.ONE
        }
        
        val dateString = formatDateForApi(date)
        
        // If date previously failed, don't retry in this session
        if (dateString in failedDates) {
            Log.d(TAG, "Skipping rate fetch for failed date: $dateString")
            return null
        }
        
        // Try to get EUR rates for this date
        val eurRates = getOrFetchEurRates(dateString)
        if (eurRates == null) {
            Log.w(TAG, "Failed to get EUR rates for $dateString")
            return null
        }
        
        // Derive rate using pivot formula
        return deriveRate(baseCurrency, quoteCurrency, eurRates)
    }
    
    /**
     * Fetch multiple rates for a base currency.
     * For pivot strategy, this returns EUR→X rates for all requested/available currencies.
     */
    override suspend fun fetchRates(baseCurrency: String, date: Long): Map<String, BigDecimal> {
        val dateString = formatDateForApi(date)
        
        // If date previously failed, return empty
        if (dateString in failedDates) {
            Log.d(TAG, "Skipping rates fetch for failed date: $dateString")
            return emptyMap()
        }
        
        val eurRates = getOrFetchEurRates(dateString)
        if (eurRates == null) {
            Log.w(TAG, "Failed to get EUR rates for $dateString")
            return emptyMap()
        }
        
        // If base is EUR, return EUR rates directly
        if (baseCurrency == PIVOT_CURRENCY) {
            return eurRates
        }
        
        // Otherwise, derive rates from EUR pivot
        // Return map of quoteCurrency -> rate(baseCurrency -> quoteCurrency)
        val result = mutableMapOf<String, BigDecimal>()
        result[baseCurrency] = BigDecimal.ONE // base to itself
        
        eurRates.keys.forEach { quoteCurrency ->
            if (quoteCurrency != baseCurrency) {
                val derived = deriveRate(baseCurrency, quoteCurrency, eurRates)
                if (derived != null) {
                    result[quoteCurrency] = derived
                }
            }
        }
        
        // Also include EUR as a target (since it's not in eurRates map)
        if (baseCurrency != PIVOT_CURRENCY) {
            val eurRate = deriveRate(baseCurrency, PIVOT_CURRENCY, eurRates)
            if (eurRate != null) {
                result[PIVOT_CURRENCY] = eurRate
            }
        }
        
        return result
    }
    
    /**
     * Fetch EUR rates for all available currencies on a given date.
     * Used for batch reconciliation - fetches all symbols at once.
     */
    suspend fun fetchAllEurRates(date: Long): Map<String, BigDecimal>? {
        val dateString = formatDateForApi(date)
        return getOrFetchEurRates(dateString, symbols = null)
    }
    
    /**
     * Fetch EUR rates for specific currencies on a given date.
     * Used for targeted batch fetching.
     */
    suspend fun fetchEurRatesForSymbols(date: Long, symbols: Set<String>): Map<String, BigDecimal>? {
        if (symbols.isEmpty()) return emptyMap()
        
        val dateString = formatDateForApi(date)
        
        // Filter out EUR from symbols (it would be the base)
        val filteredSymbols = symbols.filter { it != PIVOT_CURRENCY }.toSet()
        if (filteredSymbols.isEmpty()) {
            return emptyMap()
        }
        
        return getOrFetchEurRates(dateString, filteredSymbols.joinToString(","))
    }
    
    /**
     * Derive rate A→B from EUR rates using pivot formula.
     * Formula: A→B = (EUR→B) / (EUR→A)
     * 
     * Special cases:
     * - A == B → 1
     * - A == EUR → EUR→B (direct from rates)
     * - B == EUR → 1 / (EUR→A)
     */
    private fun deriveRate(
        baseCurrency: String,
        quoteCurrency: String,
        eurRates: Map<String, BigDecimal>
    ): BigDecimal? {
        // Same currency
        if (baseCurrency == quoteCurrency) {
            return BigDecimal.ONE
        }
        
        // EUR → X (direct lookup)
        if (baseCurrency == PIVOT_CURRENCY) {
            return eurRates[quoteCurrency]
        }
        
        // X → EUR (inverse)
        if (quoteCurrency == PIVOT_CURRENCY) {
            val eurToBase = eurRates[baseCurrency] ?: return null
            return BigDecimal.ONE.divide(eurToBase, RATE_SCALE, RoundingMode.HALF_UP)
        }
        
        // A → B via pivot: (EUR→B) / (EUR→A)
        val eurToBase = eurRates[baseCurrency] ?: return null
        val eurToQuote = eurRates[quoteCurrency] ?: return null
        
        return eurToQuote.divide(eurToBase, RATE_SCALE, RoundingMode.HALF_UP)
    }
    
    /**
     * Get EUR rates from cache or fetch from API.
     */
    private suspend fun getOrFetchEurRates(
        dateString: String,
        symbols: String? = null
    ): Map<String, BigDecimal>? {
        // Check cache first (only for full fetches without specific symbols)
        if (symbols == null && eurRatesCache.containsKey(dateString)) {
            return eurRatesCache[dateString]
        }
        
        return try {
            val response = if (dateString == "latest") {
                api.getLatestRates(base = PIVOT_CURRENCY, symbols = symbols)
            } else {
                api.getHistoricalRates(date = dateString, base = PIVOT_CURRENCY, symbols = symbols)
            }
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val rates = body.rates.mapValues { (_, value) ->
                    BigDecimal.valueOf(value).setScale(RATE_SCALE, RoundingMode.HALF_UP)
                }
                
                // Cache using the actual date from response (important for /latest)
                val actualDate = body.date
                if (symbols == null) {
                    eurRatesCache[actualDate] = rates
                    // Also cache under requested date if different (e.g., "latest" → "2024-01-15")
                    if (dateString != actualDate) {
                        eurRatesCache[dateString] = rates
                    }
                }
                
                Log.d(TAG, "Fetched ${rates.size} EUR rates for $actualDate")
                rates
            } else {
                val code = response.code()
                Log.w(TAG, "Frankfurter API error: $code for date $dateString")
                
                // Mark date as failed to avoid retry storms
                when (code) {
                    403, 404, 422, 429 -> {
                        failedDates.add(dateString)
                        Log.d(TAG, "Marking $dateString as failed (code $code)")
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching rates for $dateString", e)
            null
        }
    }
    
    /**
     * Format a timestamp as YYYY-MM-DD using local timezone.
     * The user's transaction date (local day) is used for the API request.
     */
    private fun formatDateForApi(millis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        // Use local timezone - the date the user sees is the date we query
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date(millis))
    }
    
    /**
     * Clear the in-memory caches.
     * Useful for testing or forcing a refresh.
     */
    fun clearCache() {
        eurRatesCache.clear()
        failedDates.clear()
    }
}
