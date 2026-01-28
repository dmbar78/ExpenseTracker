package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "transfer_history")
data class TransferHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: Long = System.currentTimeMillis(),
    val sourceAccount: String,
    val destinationAccount: String,
    val amount: BigDecimal,
    val currency: String,
    val comment: String? = null,
    
    // Currency conversion snapshot fields (nullable for legacy data migration)
    // Based on source account currency for same-currency-only transfers
    /** The default currency code at the time this transfer was created */
    val originalDefaultCurrencyCode: String? = null,
    /** Exchange rate from transfer currency to originalDefaultCurrency at transfer date */
    val exchangeRateToOriginalDefault: BigDecimal? = null,
    /** Amount expressed in originalDefaultCurrencyCode (amount * exchangeRateToOriginalDefault) */
    val amountInOriginalDefault: BigDecimal? = null,
    
    // Multi-currency support
    val destinationAmount: BigDecimal? = null,
    val destinationCurrency: String? = null
)