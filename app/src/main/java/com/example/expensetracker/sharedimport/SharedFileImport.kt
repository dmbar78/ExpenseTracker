package com.example.expensetracker.sharedimport

sealed interface SharedFileImportState {
    data object Idle : SharedFileImportState
    data object Processing : SharedFileImportState
    data class Ready(
        val amount: String?,
        val dateMillis: Long,
        val stagedFilePath: String,
        val mimeType: String
    ) : SharedFileImportState
    data class Error(val messageResId: Int) : SharedFileImportState
}

data class ProcessedSharedFile(
    val amount: String?,
    val dateMillis: Long,
    val stagedFilePath: String,
    val mimeType: String
)

class SharedFileImportException(val messageResId: Int) : Exception()