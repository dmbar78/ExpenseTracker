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
    val comment: String? = null
)