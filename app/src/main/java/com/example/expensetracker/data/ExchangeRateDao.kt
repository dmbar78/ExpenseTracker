package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface ExchangeRateDao {
    
    /**
     * Get a specific rate for a currency pair on a specific date.
     * Returns null if not found.
     */
    @Query("""
        SELECT * FROM exchange_rates 
        WHERE date = :date 
        AND baseCurrencyCode = :baseCurrencyCode 
        AND quoteCurrencyCode = :quoteCurrencyCode
        LIMIT 1
    """)
    suspend fun getRate(date: Long, baseCurrencyCode: String, quoteCurrencyCode: String): ExchangeRate?
    
    /**
     * Get the most recent rate for a currency pair (on or before the given date).
     * Useful for finding the latest known rate when exact date is not available.
     */
    @Query("""
        SELECT * FROM exchange_rates 
        WHERE baseCurrencyCode = :baseCurrencyCode 
        AND quoteCurrencyCode = :quoteCurrencyCode 
        AND date <= :date
        ORDER BY date DESC
        LIMIT 1
    """)
    suspend fun getMostRecentRate(date: Long, baseCurrencyCode: String, quoteCurrencyCode: String): ExchangeRate?
    
    /**
     * Get the earliest rate for a currency pair (on or after the given date).
     * Useful as fallback when no historical rate exists but a future rate was manually entered.
     */
    @Query("""
        SELECT * FROM exchange_rates 
        WHERE baseCurrencyCode = :baseCurrencyCode 
        AND quoteCurrencyCode = :quoteCurrencyCode 
        AND date >= :date
        ORDER BY date ASC
        LIMIT 1
    """)
    suspend fun getEarliestRateOnOrAfter(date: Long, baseCurrencyCode: String, quoteCurrencyCode: String): ExchangeRate?
    
    /**
     * Insert or replace an exchange rate.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(rate: ExchangeRate)
    
    /**
     * Insert multiple rates at once.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rates: List<ExchangeRate>)
    
    /**
     * Get all rates for a given base currency (observable).
     */
    @Query("SELECT * FROM exchange_rates WHERE baseCurrencyCode = :baseCurrencyCode ORDER BY date DESC")
    fun getRatesForBaseCurrency(baseCurrencyCode: String): Flow<List<ExchangeRate>>
    
    /**
     * Get all rates (observable).
     */
    @Query("SELECT * FROM exchange_rates ORDER BY date DESC")
    fun getAllRates(): Flow<List<ExchangeRate>>
    
    /**
     * Delete all rates.
     */
    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAll()
    
    /**
     * Check if a rate exists for a specific date and pair.
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM exchange_rates 
        WHERE date = :date 
        AND baseCurrencyCode = :baseCurrencyCode 
        AND quoteCurrencyCode = :quoteCurrencyCode
    """)
    suspend fun hasRate(date: Long, baseCurrencyCode: String, quoteCurrencyCode: String): Boolean
}
