package com.example.expensetracker.data

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Frankfurter API response for exchange rates.
 * Example response:
 * {
 *   "amount": 1.0,
 *   "base": "EUR",
 *   "date": "2024-01-15",
 *   "rates": {
 *     "USD": 1.0876,
 *     "GBP": 0.8543
 *   }
 * }
 */
data class FrankfurterResponse(
    val amount: Double,
    val base: String,
    val date: String,
    val rates: Map<String, Double>
)

/**
 * Retrofit interface for the Frankfurter API.
 * Base URL: https://api.frankfurter.dev/v1/
 * 
 * No API key required.
 * Dates are UTC-based.
 */
interface FrankfurterApi {
    
    /**
     * Get latest exchange rates (latest available working day).
     * @param base The base currency (default EUR)
     * @param symbols Comma-separated list of target currencies
     */
    @GET("latest")
    suspend fun getLatestRates(
        @Query("base") base: String = "EUR",
        @Query("symbols") symbols: String? = null
    ): Response<FrankfurterResponse>
    
    /**
     * Get historical exchange rates for a specific date.
     * @param date The date in YYYY-MM-DD format
     * @param base The base currency (default EUR)
     * @param symbols Comma-separated list of target currencies
     */
    @GET("{date}")
    suspend fun getHistoricalRates(
        @Path("date") date: String,
        @Query("base") base: String = "EUR",
        @Query("symbols") symbols: String? = null
    ): Response<FrankfurterResponse>
    
    companion object {
        private const val BASE_URL = "https://api.frankfurter.dev/v1/"
        
        /**
         * Create a Frankfurter API instance.
         */
        fun create(): FrankfurterApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FrankfurterApi::class.java)
        }
    }
}
