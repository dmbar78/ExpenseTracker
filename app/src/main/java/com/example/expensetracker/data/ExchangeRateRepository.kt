package com.example.expensetracker.data

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.Calendar

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
 */
class ExchangeRateRepository(
    private val exchangeRateDao: ExchangeRateDao,
    private val ratesProvider: RatesProvider = OfflineRatesProvider()
) {
    
    /**
     * Get the exchange rate for a currency pair on a specific date.
     * - If base == quote, returns 1 immediately (no DB lookup needed).
     * - If cached in DB, returns the cached rate.
     * - Otherwise, tries to fetch from provider and cache.
     * 
     * @return RateResult.Success with rate, or RateResult.Missing if unavailable.
     */
    suspend fun getRate(baseCurrency: String, quoteCurrency: String, date: Long): RateResult {
        // Same currency always has rate 1
        if (baseCurrency == quoteCurrency) {
            return RateResult.Success(BigDecimal.ONE)
        }
        
        val normalizedDate = normalizeToStartOfDay(date)
        
        // Check cache first
        val cached = exchangeRateDao.getRate(normalizedDate, baseCurrency, quoteCurrency)
        if (cached != null) {
            return RateResult.Success(cached.rate)
        }
        
        // Try to fetch from provider
        val fetched = ratesProvider.fetchRate(baseCurrency, quoteCurrency, normalizedDate)
        if (fetched != null) {
            // Cache the fetched rate
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
     * Get the rate as BigDecimal or null if missing.
     */
    suspend fun getRateOrNull(baseCurrency: String, quoteCurrency: String, date: Long): BigDecimal? {
        return when (val result = getRate(baseCurrency, quoteCurrency, date)) {
            is RateResult.Success -> result.rate
            is RateResult.Missing -> null
        }
    }
    
    /**
     * Check if a rate exists (either same currency or in DB).
     */
    suspend fun hasRate(baseCurrency: String, quoteCurrency: String, date: Long): Boolean {
        if (baseCurrency == quoteCurrency) return true
        return exchangeRateDao.hasRate(normalizeToStartOfDay(date), baseCurrency, quoteCurrency)
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
     * Reconcile rates needed for expenses and transfers.
     * This method identifies transactions with missing snapshot fields and attempts to fill them.
     * Designed to be callable from ViewModel or a future Worker.
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
        var missingCount = 0
        
        // Process expenses with missing snapshot fields
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
        
        // Process transfers with missing snapshot fields
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
