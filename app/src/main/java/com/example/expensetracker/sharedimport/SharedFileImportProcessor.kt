package com.example.expensetracker.sharedimport

import android.content.Context
import android.net.Uri
import com.example.expensetracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

interface SharedFileImportProcessor {
    suspend fun process(uri: Uri, declaredMimeType: String?): ProcessedSharedFile
    fun deleteStagedFile(path: String)

    companion object {
        const val JPEG_MIME = "image/jpeg"
        const val JPG_MIME = "image/jpg"
        const val PNG_MIME = "image/png"
        const val PDF_MIME = "application/pdf"
    }
}

class DefaultSharedFileImportProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val textExtractor: ReceiptTextExtractor
) : SharedFileImportProcessor {
    override suspend fun process(uri: Uri, declaredMimeType: String?): ProcessedSharedFile = withContext(Dispatchers.IO) {
        val mimeType = normalizeMimeType(declaredMimeType ?: context.contentResolver.getType(uri))
            ?: throw SharedFileImportException(R.string.err_shared_file_unsupported)
        val stagedFile = stage(uri, mimeType)
        try {
            validateSignature(stagedFile, mimeType)
            val parsed = ReceiptTextParser.parse(textExtractor.extract(stagedFile, mimeType), Locale.getDefault())
            ProcessedSharedFile(parsed.amount?.toPlainString(), parsed.dateMillis, stagedFile.absolutePath, mimeType)
        } catch (error: SharedFileImportException) {
            stagedFile.delete()
            throw error
        } catch (error: CancellationException) {
            stagedFile.delete()
            throw error
        } catch (_: Exception) {
            stagedFile.delete()
            throw SharedFileImportException(R.string.err_shared_file_processing)
        }
    }

    override fun deleteStagedFile(path: String) {
        runCatching { File(path).takeIf { it.parentFile?.name == CACHE_DIRECTORY }?.delete() }
    }

    private fun stage(uri: Uri, mimeType: String): File {
        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        val extension = when (mimeType) {
            SharedFileImportProcessor.PDF_MIME -> "pdf"
            SharedFileImportProcessor.PNG_MIME -> "png"
            else -> "jpg"
        }
        val output = File(directory, "shared_${UUID.randomUUID()}.$extension")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw SharedFileImportException(R.string.err_shared_file_unreadable)
            input.use { source -> output.outputStream().use(source::copyTo) }
            return output
        } catch (error: SharedFileImportException) {
            output.delete()
            throw error
        } catch (_: IOException) {
            output.delete()
            throw SharedFileImportException(R.string.err_shared_file_unreadable)
        } catch (_: SecurityException) {
            output.delete()
            throw SharedFileImportException(R.string.err_shared_file_unreadable)
        }
    }

    private fun validateSignature(file: File, mimeType: String) {
        val header = ByteArray(8)
        val count = file.inputStream().use { it.read(header) }
        val valid = when (mimeType) {
            SharedFileImportProcessor.JPEG_MIME -> count >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
            SharedFileImportProcessor.PNG_MIME -> count >= 8 && header.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            SharedFileImportProcessor.PDF_MIME -> count >= 5 && header.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-"
            else -> false
        }
        if (!valid) throw SharedFileImportException(R.string.err_shared_file_unsupported)
    }

    private fun normalizeMimeType(mimeType: String?): String? = when (mimeType?.lowercase(Locale.ROOT)) {
        SharedFileImportProcessor.JPEG_MIME, SharedFileImportProcessor.JPG_MIME -> SharedFileImportProcessor.JPEG_MIME
        SharedFileImportProcessor.PNG_MIME -> SharedFileImportProcessor.PNG_MIME
        SharedFileImportProcessor.PDF_MIME -> SharedFileImportProcessor.PDF_MIME
        else -> null
    }

    private companion object {
        private const val CACHE_DIRECTORY = "shared_imports"
    }
}