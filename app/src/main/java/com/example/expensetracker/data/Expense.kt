package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val account: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val expenseDate: Long = System.currentTimeMillis(),
    val type: String, // "Expense" or "Income"
    val comment: String? = null
)