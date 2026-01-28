package com.example.expensetracker.data

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * FawazAhmed API response wrapper.
 * The JSON structure is dynamic:
 * {
 *   "date": "2024-03-06",
 *   "eur": { "usd": 1.0, ... }
 * }
 * We use a Map<String, Any> to parse the dynamic currency key (e.g., "eur", "usd"),
 * or we can use a custom deserializer. 
 * For simplicity, we can fetch as Map<String, Any> and manually extract.
 */
// Since the key is dynamic ("eur", "usd"), it's easier to parse as raw map or use a custom logical wrapper.
// Let's use Map<String, Any> for the generic structure logic in the provider.

interface FawazAhmedApi {

    @GET
    suspend fun getRates(@Url url: String): Response<Map<String, Any>>

    companion object {
        fun create(): FawazAhmedApi {
            return Retrofit.Builder()
                .baseUrl("https://localhost/") // Dummy base URL, we use absolute @Url
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FawazAhmedApi::class.java)
        }
    }
}
