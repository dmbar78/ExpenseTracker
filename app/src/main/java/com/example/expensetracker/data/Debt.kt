package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = Expense::class,
            parentColumns = ["id"],
            childColumns = ["parentExpenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentExpenseId"])]
)
data class Debt(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val parentExpenseId: Int,
    val notes: String? = null,
    val status: String = "OPEN" // OPEN, CLOSED
)
