package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table for many-to-many relationship between Expense and Keyword.
 * Cascade delete ensures links are removed when either the expense or keyword is deleted.
 */
@Entity(
    tableName = "expense_keyword_cross_ref",
    primaryKeys = ["expenseId", "keywordId"],
    foreignKeys = [
        ForeignKey(
            entity = Expense::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Keyword::class,
            parentColumns = ["id"],
            childColumns = ["keywordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["expenseId"]),
        Index(value = ["keywordId"])
    ]
)
data class ExpenseKeywordCrossRef(
    val expenseId: Int,
    val keywordId: Int
)
