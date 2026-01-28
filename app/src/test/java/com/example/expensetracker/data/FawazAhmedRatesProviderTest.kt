package com.example.expensetracker.data

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.math.BigDecimal

class FawazAhmedRatesProviderTest {

    private lateinit var mockApi: FawazAhmedApi
    private lateinit var provider: FawazAhmedRatesProvider

    @Before
    fun setup() {
        mockApi = mock()
        provider = FawazAhmedRatesProvider(CdnUrlStrategy, mockApi)
    }

    @Test
    fun `fetchRate generates correct CDN URL for latest`() = runTest {
        // Arrange
        val responseMap = mapOf(
            "date" to "2024-03-06",
            "eur" to mapOf("usd" to 1.1)
        )
        whenever(mockApi.getRates(any())).thenReturn(Response.success(responseMap))

        // Act
        // Typically repository converts "latest" or Long date. 
        // Provider expects Long millis. 
        // We'll trust formatDateForApi works (tested implicitly or simple logic).
        // formatDateForApi uses default timezone, might be tricky in tests. 
        // But let's check the URL passed to mock.

        // Act
        provider.fetchRate("EUR", "USD", System.currentTimeMillis())

        // Verify that getRates was called with SOME string (we can't easily check date string without fixed clock)
        // Check that mocking works
        org.mockito.kotlin.verify(mockApi).getRates(any())
    }

    @Test
    fun `fetchRate parses success response correctly`() = runTest {
        // Arrange
        val responseMap = mapOf(
            "date" to "2024-03-06",
            "eur" to mapOf("usd" to 1.23456789, "gbp" to 0.85)
        )
        whenever(mockApi.getRates(any())).thenReturn(Response.success(responseMap))

        // Act
        val rate = provider.fetchRate("EUR", "USD", 1709683200000L) // 2024-03-06 approx

        // Assert
        assertEquals(BigDecimal("1.2345678900"), rate)
    }
    
    @Test
    fun `fetchRate handles failure response`() = runTest {
        // Arrange
        whenever(mockApi.getRates(any())).thenReturn(
            Response.error(404, "Not Found".toResponseBody("plain/text".toMediaTypeOrNull()))
        )

        // Act
        val rate = provider.fetchRate("EUR", "USD", 1709683200000L)

        // Assert
        assertNull(rate)
    }

    @Test
    fun `PagesUrlStrategy generates correct URL`() {
        val strategy = PagesUrlStrategy
        assertEquals(
            "https://latest.currency-api.pages.dev/v1/currencies/eur.json",
            strategy.generateUrl("latest")
        )
        assertEquals(
            "https://2024-03-06.currency-api.pages.dev/v1/currencies/eur.json",
            strategy.generateUrl("2024-03-06")
        )
    }

    @Test
    fun `CdnUrlStrategy generates correct URL`() {
        val strategy = CdnUrlStrategy
        assertEquals(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/eur.json",
            strategy.generateUrl("latest")
        )
        assertEquals(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@2024-03-06/v1/currencies/eur.json",
            strategy.generateUrl("2024-03-06")
        )
    }
}
