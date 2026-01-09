package com.example.expensetracker.viewmodel

import java.math.BigDecimal

// Data class for parsed expense/income
data class ParsedExpense(
    val accountName: String,
    val amount: BigDecimal,
    val categoryName: String,
    var expenseDate: Long = System.currentTimeMillis(),
    val type: String // "Expense" or "Income"
)

// Data class for parsed transfers
data class ParsedTransfer(
    val sourceAccountName: String,
    val destAccountName: String,
    val amount: BigDecimal,
    val transferDate: Long = System.currentTimeMillis(),
    val comment: String? = null
)

// Defines the state of the voice recognition process
sealed class VoiceRecognitionState {
    object Idle : VoiceRecognitionState()
    data class RecognitionFailed(val message: String) : VoiceRecognitionState()
    data class TransferCurrencyMismatch(val message: String) : VoiceRecognitionState()
    data class SameAccountTransfer(val message: String) : VoiceRecognitionState()
    data class Success(val message: String) : VoiceRecognitionState()
}
