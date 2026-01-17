package com.example.expensetracker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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

/**
 * Repository for managing exchange rates.
 * Handles caching in Room and fetching from RatesProvider.
 * 
 * Uses EUR as pivot currency - stores EUR→X rates and derives other pairs.
 */
class ExchangeRateRepository(
    private val exchangeRateDao: ExchangeRateDao,
    private val ratesProvider: RatesProvider = OfflineRatesProvider()
) {
    
    companion object {
        private const val PIVOT_CURRENCY = "EUR"
        private const val RATE_SCALE = 10
    }
    
    // Mutex for in-flight deduplication
    private val fetchMutex = Mutex()
    private val inFlightFetches = mutableMapOf<String, Boolean>()
    
    /**
     * Get the exchange rate for a currency pair on a specific date.
     * - If base == quote, returns 1 immediately.
     * - First tries direct lookup in DB cache.
     * - Then tries EUR-pivot derivation from cached EUR→X rates.
     * - Finally falls back to provider fetch (which also uses EUR pivot).
     * 
     * @return RateResult.Success with rate, or RateResult.Missing if unavailable.
     */
    suspend fun getRate(baseCurrency: String, quoteCurrency: String, date: Long): RateResult {
        // Same currency always has rate 1
        if (baseCurrency == quoteCurrency) {
            return RateResult.Success(BigDecimal.ONE)
        }
        
        val normalizedDate = normalizeToStartOfDay(date)
        
        // 1. Check direct cache first
        val cached = exchangeRateDao.getRate(normalizedDate, baseCurrency, quoteCurrency)
        if (cached != null) {
            return RateResult.Success(cached.rate)
        }
        
        // 2. Try to derive from cached EUR rates (pivot approach)
        val derivedFromCache = deriveRateFromEurCache(baseCurrency, quoteCurrency, normalizedDate)
        if (derivedFromCache != null) {
            return RateResult.Success(derivedFromCache)
        }
        
        // 3. Fetch from provider (will also use EUR pivot internally)
        val fetched = ratesProvider.fetchRate(baseCurrency, quoteCurrency, normalizedDate)
        if (fetched != null) {
            // Note: Provider may have cached EUR rates; we store the derived rate for direct lookup later
            exchangeRateDao.insertOrUpdate(
                ExchangeRate(
                    date = normalizedDate,
                    baseCurrencyCode = baseCurrency,
                    quoteCurrencyCode = quoteCurrency,
                    rate = fetched
                )
            )
            return RateResult.Success(fetched)
        }
        
        return RateResult.Missing
    }
    
    /**
     * Try to derive a rate from cached EUR→X rates using pivot formula.
     * A→B = (EUR→B) / (EUR→A)
     */
    private suspend fun deriveRateFromEurCache(
        baseCurrency: String,
        quoteCurrency: String,
        normalizedDate: Long
    ): BigDecimal? {
        // EUR → X (direct lookup)
        if (baseCurrency == PIVOT_CURRENCY) {
            val cached = exchangeRateDao.getRate(normalizedDate, PIVOT_CURRENCY, quoteCurrency)
            return cached?.rate
        }
        
        // X → EUR (inverse of EUR→X)
        if (quoteCurrency == PIVOT_CURRENCY) {
            val cached = exchangeRateDao.getRate(normalizedDate, PIVOT_CURRENCY, baseCurrency)
            if (cached != null) {
                return BigDecimal.ONE.divide(cached.rate, RATE_SCALE, RoundingMode.HALF_UP)
            }
            return null
        }
        
        // A → B: need both EUR→A and EUR→B
        val eurToBase = exchangeRateDao.getRate(normalizedDate, PIVOT_CURRENCY, baseCurrency)?.rate
        val eurToQuote = exchangeRateDao.getRate(normalizedDate, PIVOT_CURRENCY, quoteCurrency)?.rate
        
        if (eurToBase != null && eurToQuote != null) {
            return eurToQuote.divide(eurToBase, RATE_SCALE, RoundingMode.HALF_UP)
        }
        
        return null
    }
    
    /**
     * Get the rate as BigDecimal or null if missing.
     */
    suspend fun getRateOrNull(baseCurrency: String, quoteCurrency: String, date: Long): BigDecimal? {
        return when (val result = getRate(baseCurrency, quoteCurrency, date)) {
            is RateResult.Success -> result.rate
            is RateResult.Missing -> null
        }
    }
    
    /**
     * Check if a rate exists (either same currency, in DB, or derivable from EUR pivot).
     */
    suspend fun hasRate(baseCurrency: String, quoteCurrency: String, date: Long): Boolean {
        if (baseCurrency == quoteCurrency) return true
        val normalizedDate = normalizeToStartOfDay(date)
        
        // Direct lookup
        if (exchangeRateDao.hasRate(normalizedDate, baseCurrency, quoteCurrency)) {
            return true
        }
        
        // Check if derivable from EUR pivot
        return deriveRateFromEurCache(baseCurrency, quoteCurrency, normalizedDate) != null
    }
    
    /**
     * Insert or update a rate in the cache.
     */
    suspend fun setRate(baseCurrency: String, quoteCurrency: String, date: Long, rate: BigDecimal) {
        exchangeRateDao.insertOrUpdate(
            ExchangeRate(
                date = normalizeToStartOfDay(date),
                baseCurrencyCode = baseCurrency,
                quoteCurrencyCode = quoteCurrency,
                rate = rate
            )
        )
    }
    
    /**
     * Get all rates as a flow (for UI observation).
     */
    fun getAllRates(): Flow<List<ExchangeRate>> = exchangeRateDao.getAllRates()
    
    /**
     * Get the most recent rate for a currency pair on or before the given date.
     * Uses EUR pivot derivation if direct rate is not found.
     * Returns null if no rate is available.
     */
    suspend fun getMostRecentRateOnOrBefore(baseCurrency: String, quoteCurrency: String, date: Long): BigDecimal? {
        if (baseCurrency == quoteCurrency) return BigDecimal.ONE
        
        val normalizedDate = normalizeToStartOfDay(date)
        
        // Try direct lookup first
        val direct = exchangeRateDao.getMostRecentRate(normalizedDate, baseCurrency, quoteCurrency)
        if (direct != null) return direct.rate
        
        // Try EUR pivot derivation from most recent EUR rates
        return deriveMostRecentRateFromEurPivot(baseCurrency, quoteCurrency, normalizedDate)
    }
    
    /**
     * Derive the most recent rate using EUR pivot (on or before date).
     */
    private suspend fun deriveMostRecentRateFromEurPivot(
        baseCurrency: String,
        quoteCurrency: String,
        normalizedDate: Long
    ): BigDecimal? {
        // EUR → X (direct lookup)
        if (baseCurrency == PIVOT_CURRENCY) {
            val cached = exchangeRateDao.getMostRecentRate(normalizedDate, PIVOT_CURRENCY, quoteCurrency)
            return cached?.rate
        }
        
        // X → EUR (inverse of EUR→X)
        if (quoteCurrency == PIVOT_CURRENCY) {
            val cached = exchangeRateDao.getMostRecentRate(normalizedDate, PIVOT_CURRENCY, baseCurrency)
            if (cached != null) {
                return BigDecimal.ONE.divide(cached.rate, RATE_SCALE, RoundingMode.HALF_UP)
            }
            return null
        }
        
        // A → B: need both EUR→A and EUR→B (most recent on or before)
        val eurToBase = exchangeRateDao.getMostRecentRate(normalizedDate, PIVOT_CURRENCY, baseCurrency)?.rate
        val eurToQuote = exchangeRateDao.getMostRecentRate(normalizedDate, PIVOT_CURRENCY, quoteCurrency)?.rate
        
        if (eurToBase != null && eurToQuote != null) {
            return eurToQuote.divide(eurToBase, RATE_SCALE, RoundingMode.HALF_UP)
        }
        
        return null
    }
    
    /**
     * Check if EUR pivot rate exists for a currency on or before date.
     * This is used for default currency guard check.
     */
    suspend fun hasEurPivotOnOrBefore(quoteCurrency: String, date: Long): Boolean {
        if (quoteCurrency == PIVOT_CURRENCY) return true
        val normalizedDate = normalizeToStartOfDay(date)
        return exchangeRateDao.getMostRecentRate(normalizedDate, PIVOT_CURRENCY, quoteCurrency) != null
    }
    
    /**
     * Try to fetch EUR pivot rate for a currency from the provider.
     * Returns true if rate was fetched and stored, false otherwise.
     */
    suspend fun fetchAndStoreEurPivot(quoteCurrency: String, date: Long): Boolean {
        if (quoteCurrency == PIVOT_CURRENCY) return true
        
        val normalizedDate = normalizeToStartOfDay(date)
        val fetched = ratesProvider.fetchRate(PIVOT_CURRENCY, quoteCurrency, normalizedDate)
        if (fetched != null) {
            exchangeRateDao.insertOrUpdate(
                ExchangeRate(
                    date = normalizedDate,
                    baseCurrencyCode = PIVOT_CURRENCY,
                    quoteCurrencyCode = quoteCurrency,
                    rate = fetched
                )
            )
            return true
        }
        return false
    }
    
    /**
     * Reconcile rates needed for expenses and transfers.
     * 
     * Uses batched fetching: groups missing rates by day, fetches all EUR→X rates for each day
     * in a single API call, then processes transactions using EUR pivot derivation.
     * 
     * @param expenses List of expenses to reconcile
     * @param transfers List of transfers to reconcile
     * @param defaultCurrency The current default currency code
     * @param onExpenseUpdate Callback when an expense's snapshot fields should be updated
     * @param onTransferUpdate Callback when a transfer's snapshot fields should be updated
     * @return Number of items that still need rates (couldn't be reconciled)
     */
    suspend fun reconcileRatesNeeded(
        expenses: List<Expense>,
        transfers: List<TransferHistory>,
        defaultCurrency: String,
        onExpenseUpdate: suspend (Expense) -> Unit,
        onTransferUpdate: suspend (TransferHistory) -> Unit
    ): Int {
        // Step 1: Group missing work by normalized day
        data class RateNeed(val currency: String, val date: Long)
        
        val needsByDay = mutableMapOf<Long, MutableSet<String>>()
        
        // Collect currencies needed per day from expenses
        expenses.filter { it.amountInOriginalDefault == null }.forEach { expense ->
            val normalizedDate = normalizeToStartOfDay(expense.expenseDate)
            if (expense.currency != defaultCurrency) {
                needsByDay.getOrPut(normalizedDate) { mutableSetOf() }.apply {
                    add(expense.currency)
                    if (defaultCurrency != PIVOT_CURRENCY) add(defaultCurrency)
                }
            }
        }
        
        // Collect currencies needed per day from transfers
        transfers.filter { it.amountInOriginalDefault == null }.forEach { transfer ->
            val normalizedDate = normalizeToStartOfDay(transfer.date)
            if (transfer.currency != defaultCurrency) {
                needsByDay.getOrPut(normalizedDate) { mutableSetOf() }.apply {
                    add(transfer.currency)
                    if (defaultCurrency != PIVOT_CURRENCY) add(defaultCurrency)
                }
            }
        }
        
        // Step 2: Batch fetch EUR rates for each day (with in-flight deduplication)
        for ((normalizedDate, currencies) in needsByDay) {
            // Skip EUR since it's the pivot
            val neededSymbols = currencies.filter { it != PIVOT_CURRENCY }.toSet()
            if (neededSymbols.isEmpty()) continue
            
            val dateKey = formatDateKey(normalizedDate)
            
            // In-flight deduplication
            val shouldFetch = fetchMutex.withLock {
                if (inFlightFetches[dateKey] == true) {
                    false
                } else {
                    inFlightFetches[dateKey] = true
                    true
                }
            }
            
            if (shouldFetch) {
                try {
                    // Fetch EUR rates for all needed currencies
                    val provider = ratesProvider
                    if (provider is FrankfurterRatesProvider) {
                        val eurRates = provider.fetchEurRatesForSymbols(normalizedDate, neededSymbols)
                        if (eurRates != null) {
                            // Store all EUR→X rates
                            val ratesToInsert = eurRates.map { (quote, rate) ->
                                ExchangeRate(
                                    date = normalizedDate,
                                    baseCurrencyCode = PIVOT_CURRENCY,
                                    quoteCurrencyCode = quote,
                                    rate = rate
                                )
                            }
                            if (ratesToInsert.isNotEmpty()) {
                                exchangeRateDao.insertAll(ratesToInsert)
                            }
                        }
                    } else {
                        // Fallback for non-Frankfurter providers: fetch one by one
                        neededSymbols.forEach { currency ->
                            ratesProvider.fetchRate(PIVOT_CURRENCY, currency, normalizedDate)?.let { rate ->
                                exchangeRateDao.insertOrUpdate(
                                    ExchangeRate(
                                        date = normalizedDate,
                                        baseCurrencyCode = PIVOT_CURRENCY,
                                        quoteCurrencyCode = currency,
                                        rate = rate
                                    )
                                )
                            }
                        }
                    }
                } finally {
                    fetchMutex.withLock {
                        inFlightFetches.remove(dateKey)
                    }
                }
            }
        }
        
        // Step 3: Process expenses using cached rates (now with EUR pivot derivation)
        var missingCount = 0
        
        expenses.filter { it.amountInOriginalDefault == null }.forEach { expense ->
            val rateResult = getRate(expense.currency, defaultCurrency, expense.expenseDate)
            when (rateResult) {
                is RateResult.Success -> {
                    val updatedExpense = expense.copy(
                        originalDefaultCurrencyCode = defaultCurrency,
                        exchangeRateToOriginalDefault = rateResult.rate,
                        amountInOriginalDefault = expense.amount.multiply(rateResult.rate)
                    )
                    onExpenseUpdate(updatedExpense)
                }
                is RateResult.Missing -> missingCount++
            }
        }
        
        // Step 4: Process transfers using cached rates
        transfers.filter { it.amountInOriginalDefault == null }.forEach { transfer ->
            val rateResult = getRate(transfer.currency, defaultCurrency, transfer.date)
            when (rateResult) {
                is RateResult.Success -> {
                    val updatedTransfer = transfer.copy(
                        originalDefaultCurrencyCode = defaultCurrency,
                        exchangeRateToOriginalDefault = rateResult.rate,
                        amountInOriginalDefault = transfer.amount.multiply(rateResult.rate)
                    )
                    onTransferUpdate(updatedTransfer)
                }
                is RateResult.Missing -> missingCount++
            }
        }
        
        return missingCount
    }
    
    /**
     * Format a normalized date as a key for deduplication.
     */
    private fun formatDateKey(normalizedDate: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(normalizedDate)
    }
    
    /**
     * Normalize a timestamp to the start of day (00:00:00.000).
     */
    private fun normalizeToStartOfDay(millis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
