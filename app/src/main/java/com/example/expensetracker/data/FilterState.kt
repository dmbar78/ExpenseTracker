package com.example.expensetracker.data

import java.util.Calendar

/**
 * Represents different time filter modes for filtering transactions.
 */
sealed class TimeFilter {
    /** No time filter applied */
    data object None : TimeFilter()
    
    /** Filter by a specific day (stores the day's start millis) */
    data class Day(val dateMillis: Long) : TimeFilter()
    
    /** Filter by a week (Monday-Sunday range, stores Monday's start millis) */
    data class Week(val weekStartMillis: Long) : TimeFilter()
    
    /** Filter by a specific month (stores year and month) */
    data class Month(val year: Int, val month: Int) : TimeFilter()
    
    /** Filter by a specific year */
    data class Year(val year: Int) : TimeFilter()
    
    /** Filter by a custom date range (inclusive) */
    data class Period(val startMillis: Long, val endMillis: Long) : TimeFilter()
    
    /** Filter from earliest transaction to current date ("All Time" checkbox) */
    data object AllTime : TimeFilter()
    
    /**
     * Returns the date range (start, end) in millis for this filter.
     * End millis is end-of-day (23:59:59.999).
     */
    fun toDateRange(): Pair<Long, Long>? {
        return when (this) {
            is None -> null
            is Day -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MILLISECOND, -1)
                val end = cal.timeInMillis
                start to end
            }
            is Week -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = weekStartMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 7)
                cal.add(Calendar.MILLISECOND, -1)
                val end = cal.timeInMillis
                start to end
            }
            is Month -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                cal.add(Calendar.MILLISECOND, -1)
                val end = cal.timeInMillis
                start to end
            }
            is Year -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.YEAR, 1)
                cal.add(Calendar.MILLISECOND, -1)
                val end = cal.timeInMillis
                start to end
            }
            is Period -> startMillis to endMillis
            is AllTime -> null // Handled specially - uses earliest transaction date
        }
    }
    
    /**
     * Returns a human-readable description of this filter.
     */
    fun toDisplayString(): String {
        val cal = Calendar.getInstance()
        return when (this) {
            is None -> ""
            is Day -> {
                cal.timeInMillis = dateMillis
                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(cal.time)
            }
            is Week -> {
                cal.timeInMillis = weekStartMillis
                val start = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 6)
                val end = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(cal.time)
                "$start - $end"
            }
            is Month -> {
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(cal.time)
            }
            is Year -> year.toString()
            is Period -> {
                val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                "${fmt.format(java.util.Date(startMillis))} - ${fmt.format(java.util.Date(endMillis))}"
            }
            is AllTime -> "All Time"
        }
    }
}

/**
 * Represents all active filters for the Home screen.
 */
data class FilterState(
    /** Time-based filter (Day, Week, Month, Year, Period, or None) */
    val timeFilter: TimeFilter = TimeFilter.None,
    
    /** Account filter for Expense/Income tabs (null = no filter) */
    val expenseIncomeAccount: String? = null,
    
    /** Category filter for Expense/Income tabs (null = no filter) */
    val category: String? = null,
    
    /** Source account filter for Transfers tab (null = no filter) */
    val transferSourceAccount: String? = null,
    
    /** Destination account filter for Transfers tab (null = no filter) */
    val transferDestAccount: String? = null,
    
    /** Free-text query filter for comment/keyword search (null = no filter) */
    val textQuery: String? = null
) {
    /** Returns true if any filter is active */
    fun hasActiveFilters(): Boolean {
        return timeFilter !is TimeFilter.None ||
                expenseIncomeAccount != null ||
                category != null ||
                transferSourceAccount != null ||
                transferDestAccount != null ||
                textQuery != null
    }
    
    /** Returns true if time filter is active */
    fun hasTimeFilter(): Boolean = timeFilter !is TimeFilter.None
    
    /** Returns true if expense/income account filter is active */
    fun hasAccountFilter(): Boolean = expenseIncomeAccount != null
    
    /** Returns true if category filter is active */
    fun hasCategoryFilter(): Boolean = category != null
    
    /** Returns true if any transfer filter is active */
    fun hasTransferFilter(): Boolean = transferSourceAccount != null || transferDestAccount != null
    
    /** Returns true if text query filter is active */
    fun hasTextQueryFilter(): Boolean = textQuery != null
}

/**
 * Helper to get Monday of the week containing the given date.
 */
fun getWeekStartMillis(dateMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = dateMillis
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/**
 * Helper to get start of day for a given date.
 */
fun getDayStartMillis(dateMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/**
 * Helper to get end of day for a given date (23:59:59.999).
 */
fun getDayEndMillis(dateMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    return cal.timeInMillis
}
