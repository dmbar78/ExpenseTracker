package com.example.expensetracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "accounts", indices = [Index(value = ["name"], unique = true)])
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,
    val currency: String,
    val balance: BigDecimal
)