package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "currencies", indices = [Index(value = ["code"], unique = true)])
data class Currency(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val code: String, // e.g., "USD"
    val name: String  // e.g., "United States Dollar"
)