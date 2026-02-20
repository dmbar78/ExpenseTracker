package com.example.expensetracker.util

import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Category
import me.xdrop.fuzzywuzzy.FuzzySearch

object FuzzyMatcher {
    private const val CONFIDENCE_THRESHOLD = 75

    fun matchAccount(query: String?, accounts: List<Account>): Account? {
        if (query.isNullOrBlank() || accounts.isEmpty()) return null
        val accountNames = accounts.map { it.name }
        val match = FuzzySearch.extractOne(query, accountNames)
        return if (match.score >= CONFIDENCE_THRESHOLD) {
            accounts.find { it.name == match.string }
        } else {
            null
        }
    }

    fun matchCategory(query: String?, categories: List<Category>): Category? {
        if (query.isNullOrBlank() || categories.isEmpty()) return null
        val categoryNames = categories.map { it.name }
        val match = FuzzySearch.extractOne(query, categoryNames)
        return if (match.score >= CONFIDENCE_THRESHOLD) {
            categories.find { it.name == match.string }
        } else {
            null
        }
    }
}
