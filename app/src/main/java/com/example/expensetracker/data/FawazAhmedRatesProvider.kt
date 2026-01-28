package com.example.expensetracker.data

import android.util.Log
import com.google.gson.internal.LinkedTreeMap
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Strategy for generating FawazAhmed API URLs.
 */
interface FawazAhmedUrlStrategy {
    fun generateUrl(date: String): String
}

object CdnUrlStrategy : FawazAhmedUrlStrategy {
    override fun generateUrl(date: String): String {
        val version = if (date == "latest") "latest" else date
        return "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$version/v1/currencies/eur.json"
    }
}

object PagesUrlStrategy : FawazAhmedUrlStrategy {
    override fun generateUrl(date: String): String {
        val subdomain = if (date == "latest") "latest" else date
        return "https://$subdomain.currency-api.pages.dev/v1/currencies/eur.json"
    }
}

/**
 * RatesProvider implementation for FawazAhmed API.
 * Fetches static JSON files containing all rates for EUR base.
 */
class FawazAhmedRatesProvider(
    private val urlStrategy: FawazAhmedUrlStrategy,
    private val api: FawazAhmedApi = FawazAhmedApi.create()
) : RatesProvider {

    companion object {
        private const val TAG = "FawazAhmedProvider"
        private const val BASE_CURRENCY_LOWER = "eur" // API keys are lowercase
        private const val RATE_SCALE = 10
    }

    // In-memory cache: "YYYY-MM-DD" -> Map<CurrencyCode, Rate>
    private val ratesCache = mutableMapOf<String, Map<String, BigDecimal>>()
    private val failedDates = mutableSetOf<String>()

    override suspend fun fetchRate(baseCurrency: String, quoteCurrency: String, date: Long): BigDecimal? {
        val rates = getOrFetchRates(date) ?: return null
        return deriveRate(baseCurrency, quoteCurrency, rates)
    }

    override suspend fun fetchRates(baseCurrency: String, date: Long): Map<String, BigDecimal> {
        val rates = getOrFetchRates(date) ?: return emptyMap()
        
        // If base is EUR, return direct (normalized keys)
        if (baseCurrency.equals("EUR", ignoreCase = true)) {
            return rates
        }

        // Derive for other bases
        val result = mutableMapOf<String, BigDecimal>()
        result[baseCurrency] = BigDecimal.ONE

        rates.keys.forEach { quote ->
            if (quote != baseCurrency) {
                deriveRate(baseCurrency, quote, rates)?.let { 
                    result[quote] = it 
                }
            }
        }
        
        // Add EUR if not present (inverse)
        if (!result.containsKey("EUR")) {
             deriveRate(baseCurrency, "EUR", rates)?.let {
                 result["EUR"] = it
             }
        }
        
        return result
    }

    private fun deriveRate(base: String, quote: String, rates: Map<String, BigDecimal>): BigDecimal? {
        if (base == quote) return BigDecimal.ONE
        
        // Strategy: A -> B = (EUR->B) / (EUR->A)
        // Rates map contains EUR -> X
        
        val eurToBase = if (base.equals("EUR", ignoreCase = true)) BigDecimal.ONE else rates[base]
        val eurToQuote = if (quote.equals("EUR", ignoreCase = true)) BigDecimal.ONE else rates[quote]
        
        if (eurToBase == null || eurToQuote == null) return null
        
        return eurToQuote.divide(eurToBase, RATE_SCALE, RoundingMode.HALF_UP)
    }

    private suspend fun getOrFetchRates(date: Long): Map<String, BigDecimal>? {
        val dateString = formatDateForApi(date)
        
        if (dateString in failedDates) {
            return null
        }
        
        if (ratesCache.containsKey(dateString)) {
            return ratesCache[dateString]
        }

        // Handle "latest" logic if needed, but repository usually passes specific dates.
        // If date is today, we might use "latest" URL but map it to today's date string.
        // For distinct caching, strict usage of YYYY-MM-DD is safer.
        // The API supports explicit dates.
        // IMPORTANT: The "latest" endpoint might be useful for 'today', but explicit date is deterministic.
        // We will use explicit date unless it's literally today? 
        // Actually, for simplicity and consistency with historicals, we try explicit date first.
        // If date is in future, we fail.
        
        // Optimization: Use "latest" if date is today?
        // Risky if timezones differ between user and server updates.
        // Safe bet: always format YYYY-MM-DD. 
        // Warning: API documentation says "latest" is updated daily. 
        // Let's stick to YYYY-MM-DD.
        
        val url = urlStrategy.generateUrl(dateString)
        
        try {
            val response = api.getRates(url)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Parse: {"date": "...", "eur": {"usd": ...}}
                
                @Suppress("UNCHECKED_CAST")
                val ratesMap = body[BASE_CURRENCY_LOWER] as? Map<String, Any>
                
                if (ratesMap != null) {
                    val parsedRates = mutableMapOf<String, BigDecimal>()
                    ratesMap.forEach { (key, value) ->
                        // Value acts like a number
                        val rateVal = try {
                            BigDecimal(value.toString())
                        } catch (e: Exception) {
                            null
                        }
                        if (rateVal != null) {
                            parsedRates[key.uppercase()] = rateVal
                        }
                    }
                    
                    ratesCache[dateString] = parsedRates
                    return parsedRates
                }
            } else {
                if (response.code() in 400..499) {
                    failedDates.add(dateString)
                }
            }
        } catch (e: Exception) {
            // Network error
        }
        
        return null
    }

    private fun formatDateForApi(millis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date(millis))
    }
}
