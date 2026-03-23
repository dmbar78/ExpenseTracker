package com.example.expensetracker.viewmodel

import com.example.expensetracker.data.TimeFilter
import java.math.BigDecimal

/**
 * Represents one period's aggregated totals for the bar chart.
 */
data class PeriodBarData(
    val timeFilter: TimeFilter,
    val label: String,
    val expenseTotal: BigDecimal,
    val incomeTotal: BigDecimal,
    val delta: BigDecimal,       // income - expense (positive = profit, negative = loss)
    val hasMissingRates: Boolean
)
