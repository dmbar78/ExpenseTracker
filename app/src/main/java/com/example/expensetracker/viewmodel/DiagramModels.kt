package com.example.expensetracker.viewmodel

import java.math.BigDecimal

/**
 * Represents a single category breakdown entry with amount and percentage.
 */
data class CategoryBreakdownEntry(
    val categoryName: String,
    val amountInDefault: BigDecimal,
    val percentageOfTotal: Double // 0.0 to 100.0
)

/**
 * Represents the full category breakdown state for the current filtered expenses/incomes.
 */
data class CategoryBreakdown(
    val entries: List<CategoryBreakdownEntry>,
    val totalInDefault: BigDecimal,
    val currencyCode: String,
    val hasMissingRates: Boolean // True if any rate was missing (don't show percentages)
)

/**
 * Represents a single keyword breakdown entry within a selected category.
 */
data class KeywordBreakdownEntry(
    val keywordName: String,
    val amountInDefault: BigDecimal,
    val percentageOfCategoryTotal: Double, // 0.0 to 100.0
    val isNoKeywordBucket: Boolean = false
)

/**
 * Represents the keyword breakdown for a selected category.
 */
data class KeywordBreakdown(
    val categoryName: String,
    val entries: List<KeywordBreakdownEntry>,
    val categoryTotalInDefault: BigDecimal,
    val currencyCode: String,
    val hasMissingRates: Boolean
)
