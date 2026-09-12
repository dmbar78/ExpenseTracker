package com.example.expensetracker.sharedimport

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.MainActivity
import com.example.expensetracker.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class SharedFileImportProcessorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun supportedMimeTypesResolveToMainActivity() {
        listOf("image/jpeg", "image/jpg", "image/png", "application/pdf").forEach { mimeType ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val activities = context.packageManager.queryIntentActivities(intent, 0)

            assertTrue(
                "$mimeType should resolve to MainActivity",
                activities.any { it.activityInfo.name == MainActivity::class.java.name }
            )
        }
    }

    @Test
    fun validJpegIsStagedAndParsedWithoutRealOcr() {
        runBlocking {
            val source = File(context.cacheDir, "shared_source.jpg").apply {
                writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
            }
            val processor = DefaultSharedFileImportProcessor(
                context,
                object : ReceiptTextExtractor {
                    override suspend fun extract(file: File, mimeType: String): String =
                        "Total 12.34\nDate: 2026-09-10"
                }
            )

            val result = processor.process(Uri.fromFile(source), "image/jpeg")

            assertEquals(BigDecimal("12.34").toPlainString(), result.amount)
            assertEquals("image/jpeg", result.mimeType)
            assertTrue(File(result.stagedFilePath).exists())
            processor.deleteStagedFile(result.stagedFilePath)
            source.delete()
        }
    }

    @Test
    fun mismatchedSignatureIsRejectedAndCleanedUp() {
        runBlocking {
            val source = File(context.cacheDir, "not_really_a_png.png").apply { writeText("not a png") }
            val processor = DefaultSharedFileImportProcessor(
                context,
                object : ReceiptTextExtractor {
                    override suspend fun extract(file: File, mimeType: String): String = "Total 1.00"
                }
            )

            val error = runCatching { processor.process(Uri.fromFile(source), "image/png") }.exceptionOrNull()

            assertEquals(R.string.err_shared_file_unsupported, (error as SharedFileImportException).messageResId)
            source.delete()
        }
    }
}