package com.example.expensetracker.data.gemini

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class Money(
    val value: Double,
    val currency: String
)

data class TransactionExtract(
    @SerializedName("expense_type") val expenseType: String,
    val account: String?,
    val amount: Money?,
    @SerializedName("source_account") val sourceAccount: String?,
    @SerializedName("destination_account") val destinationAccount: String?,
    @SerializedName("source_amount") val sourceAmount: Money?,
    @SerializedName("destination_amount") val destinationAmount: Money?,
    val category: String?,
    val date: String?
)

data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

data class Content(
    val parts: List<Part>,
    val role: String? = "user"
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val temperature: Double? = 0.0,
    val responseMimeType: String? = "application/json"
)

data class GeminiResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: Content?
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    companion object {
        fun create(): GeminiApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GeminiApiService::class.java)
        }
        
        fun extractJsonObject(text: String): String {
            val candidate = text.trim()
            if (candidate.startsWith("{") && candidate.endsWith("}")) {
                return candidate
            }
            val start = candidate.indexOf("{")
            val end = candidate.lastIndexOf("}")
            if (start != -1 && end != -1 && end > start) {
                return candidate.substring(start, end + 1)
            }
            throw IllegalArgumentException("Could not parse a JSON object from model response")
        }
        
        fun buildPrompt(userText: String): String {
            return """
            Extract a single bank transaction from the TEXT and return ONLY a valid JSON object.
            Do not wrap in markdown. Do not include any extra keys.

            Rules:
            - expense_type must be one of: expense, income, transfer
            - For expense/income: include account + amount (Money)
              - amount.value: negative for expenses, positive for income, non-zero
              - amount.currency: 3-letter ISO code (euros->EUR, dollars->USD, etc.)
            - For transfer: include source_account, destination_account, source_amount, and destination_amount
              - source_amount.value must be negative (leaving source)
              - destination_amount.value must be positive (arriving to destination)
              - destination_amount is required for FX / different-currency transfers; otherwise it may be null
            - date: YYYY-MM-DD when possible; if year missing, assume current year
            - If account/category/date cannot be determined, use null
            - The JSON MUST have attributes to identify and serve creation of all types of records: expense, income, or transfer.

            TEXT: $userText
            """.trimIndent()
        }
    }
}
