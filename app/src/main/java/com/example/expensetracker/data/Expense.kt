package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val account: String,
    val amount: BigDecimal,
    val currency: String,
    val category: String,
    val expenseDate: Long = System.currentTimeMillis(),
    val type: String, // "Expense" or "Income"
    val comment: String? = null
)