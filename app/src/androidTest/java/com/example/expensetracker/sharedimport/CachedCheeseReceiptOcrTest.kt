package com.example.expensetracker.sharedimport

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class CachedCheeseReceiptOcrTest {
    @Test
    fun extractsCheeseReceiptAmount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val receipt = File(context.cacheDir, "cheese_receipt_7_14.jpg")
        assumeTrue(
            "Local receipt fixture is not available",
            testContext.assets.list("")?.contains("cheese_receipt_7_14.jpg") == true
        )
        testContext.assets.open("cheese_receipt_7_14.jpg").use { input ->
            receipt.outputStream().use(input::copyTo)
        }
        assertTrue("Receipt fixture missing: ${receipt.absolutePath}", receipt.isFile)

        val text = MlKitReceiptTextExtractor(context).extract(
            receipt,
            SharedFileImportProcessor.JPEG_MIME
        )

        assertEquals("Recognized text:\n$text", BigDecimal("7.14"), ReceiptTextParser.parseAmount(text))
    }
}
