package com.example.expensetracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "keywords", indices = [Index(value = ["name"], unique = true)])
data class Keyword(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String
)