package com.example.expensetracker.sharedimport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ReceiptTextExtractor {
    suspend fun extract(file: File, mimeType: String): String
}

class MlKitReceiptTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) : ReceiptTextExtractor {
    override suspend fun extract(file: File, mimeType: String): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            if (mimeType == SharedFileImportProcessor.PDF_MIME) {
                extractPdf(file, recognizer::process)
            } else {
                val image = InputImage.fromFilePath(context, Uri.fromFile(file))
                recognizer.process(image).await().text
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
                            output.append(recognize(InputImage.fromBitmap(bitmap, 0)).await().text)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        return output.toString()
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }

    private companion object {
        const val PDF_OCR_LONG_EDGE = 2048
    }
}