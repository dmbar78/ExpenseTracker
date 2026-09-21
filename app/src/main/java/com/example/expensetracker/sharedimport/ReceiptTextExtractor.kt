package com.example.expensetracker.sharedimport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.Text
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ReceiptTextExtractor {
    suspend fun extract(file: File, mimeType: String): String
}

class MlKitReceiptTextExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReceiptTextExtractor {
    override suspend fun extract(file: File, mimeType: String): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            if (mimeType == SharedFileImportProcessor.PDF_MIME) {
                extractPdf(file, recognizer::process)
            } else {
                val image = InputImage.fromFilePath(context, Uri.fromFile(file))
                val originalText = recognizer.process(image).await().toReceiptText()
                if (!shouldTryEnhancedOcr(originalText, Locale.getDefault(), System.currentTimeMillis(), ZoneId.systemDefault())) {
                    originalText
                } else {
                    val bitmap = decodeBoundedBitmap(file)
                    try {
                        selectEnhancedText(bitmap, originalText, recognizer::process)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            recognizer.close()
        }
    }

    private suspend fun extractPdf(
        file: File,
        recognize: (InputImage) -> Task<com.google.mlkit.vision.text.Text>
    ): String {
        val output = StringBuilder()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val renderSize = calculatePdfRenderSize(page.width, page.height, PDF_OCR_LONG_EDGE)
                        val bitmap = Bitmap.createBitmap(
                            renderSize.width,
                            renderSize.height,
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            if (output.isNotEmpty()) output.append('\n')
                            val originalText = recognize(InputImage.fromBitmap(bitmap, 0)).await().toReceiptText()
                            output.append(
                                if (shouldTryEnhancedOcr(originalText, Locale.getDefault(), System.currentTimeMillis(), ZoneId.systemDefault())) {
                                    selectEnhancedText(bitmap, originalText, recognize)
                                } else {
                                    originalText
                                }
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        return output.toString()
    }

    private fun decodeBoundedBitmap(file: File): Bitmap {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val scale = minOf(1f, IMAGE_OCR_LONG_EDGE.toFloat() / maxOf(width, height))
            decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
        }
    }

    private suspend fun selectEnhancedText(
        bitmap: Bitmap,
        originalText: String,
        recognize: (InputImage) -> Task<Text>
    ): String {
        val enhanced = enhanceReceiptBitmap(bitmap)
        return try {
            val enhancedText = recognize(InputImage.fromBitmap(enhanced, 0)).await().toReceiptText()
            selectBetterOcrText(
                originalText,
                enhancedText,
                Locale.getDefault(),
                System.currentTimeMillis(),
                ZoneId.systemDefault()
            )
        } finally {
            enhanced.recycle()
        }
    }

    private fun Text.toReceiptText(): String {
        val lines = textBlocks.flatMap { it.lines }
        val fragments = lines.mapNotNull { line ->
            line.boundingBox?.let { bounds ->
                OcrTextFragment(line.text, bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
        }
        return if (fragments.size == lines.size && fragments.isNotEmpty()) {
            reconstructOcrRows(fragments)
        } else {
            text
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }

    private companion object {
        const val PDF_OCR_LONG_EDGE = 2048
        const val IMAGE_OCR_LONG_EDGE = 2400
    }
}