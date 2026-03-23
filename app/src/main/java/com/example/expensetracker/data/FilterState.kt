package com.example.expensetracker.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

// ==================== Chart Grain & Period Helpers ====================

/**
 * Supported grain types for the Home bar-chart.
 */
enum class ChartGrain { Day, Week, Month, Year }

/**
 * Normalize a TimeFilter to its period start for chart alignment.
 */
fun TimeFilter.normalizeToPeriodStart(): TimeFilter {
    return when (this) {
        is TimeFilter.Day -> {
            TimeFilter.Day(getDayStartMillis(dateMillis))
        }
        is TimeFilter.Week -> {
            TimeFilter.Week(getWeekStartMillis(weekStartMillis))
        }
        is TimeFilter.Month -> this // already normalized
        is TimeFilter.Year -> this  // already normalized
        else -> this
    }
}

/**
 * Determine the ChartGrain for a given TimeFilter.
 */
fun TimeFilter.toChartGrain(): ChartGrain? {
    return when (this) {
        is TimeFilter.Day -> ChartGrain.Day
        is TimeFilter.Week -> ChartGrain.Week
        is TimeFilter.Month -> ChartGrain.Month
        is TimeFilter.Year -> ChartGrain.Year
        else -> null
    }
}

/**
 * Create a TimeFilter from a ChartGrain using the current date as anchor.
 */
fun chartGrainToCurrentTimeFilter(grain: ChartGrain): TimeFilter {
    val now = Calendar.getInstance()
    return when (grain) {
        ChartGrain.Day -> TimeFilter.Day(getDayStartMillis(now.timeInMillis))
        ChartGrain.Week -> TimeFilter.Week(getWeekStartMillis(now.timeInMillis))
        ChartGrain.Month -> TimeFilter.Month(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
        ChartGrain.Year -> TimeFilter.Year(now.get(Calendar.YEAR))
    }
}

/**
 * Step a TimeFilter by [delta] periods (negative = previous, positive = next).
 */
fun TimeFilter.stepBy(delta: Int): TimeFilter {
    return when (this) {
        is TimeFilter.Day -> {
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            cal.add(Calendar.DAY_OF_MONTH, delta)
            TimeFilter.Day(getDayStartMillis(cal.timeInMillis))
        }
        is TimeFilter.Week -> {
            val cal = Calendar.getInstance().apply { timeInMillis = weekStartMillis }
            cal.add(Calendar.WEEK_OF_YEAR, delta)
            TimeFilter.Week(getWeekStartMillis(cal.timeInMillis))
        }
        is TimeFilter.Month -> {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            cal.add(Calendar.MONTH, delta)
            TimeFilter.Month(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        }
        is TimeFilter.Year -> TimeFilter.Year(year + delta)
        else -> this
    }
}

/**
 * Check if this TimeFilter's period start is after the current system period.
 */
fun TimeFilter.isAfterCurrentPeriod(): Boolean {
    val now = Calendar.getInstance()
    return when (this) {
        is TimeFilter.Day -> dateMillis > getDayStartMillis(now.timeInMillis)
        is TimeFilter.Week -> weekStartMillis > getWeekStartMillis(now.timeInMillis)
        is TimeFilter.Month -> {
            val currentYear = now.get(Calendar.YEAR)
            val currentMonth = now.get(Calendar.MONTH)
            year > currentYear || (year == currentYear && month > currentMonth)
        }
        is TimeFilter.Year -> year > now.get(Calendar.YEAR)
        else -> false
    }
}

/**
 * Produce a 5-period window: the selected period in the middle (index 2) when possible,
 * with 4 neighbors. Caps right side so no period starts after current system period.
 * Returns a list of up to 5 TimeFilters ordered chronologically.
 */
fun TimeFilter.generate5PeriodWindow(): List<TimeFilter> {
    // Start with selected at center (index 2): offsets -4..-0..+0..+0
    // We want: [selected-4, selected-3, selected-2, selected-1, selected]
    // But try centered first, then shift left if right side exceeds current period.
    val raw = (-2..2).map { this.stepBy(it) }

    // Cap right side: find first index that exceeds current period
    val firstExceedIndex = raw.indexOfFirst { it.isAfterCurrentPeriod() }
    if (firstExceedIndex == -1) return raw // all within bounds

    // Shift window left
    val shift = raw.size - firstExceedIndex
    val shifted = ((-2 - shift)..(2 - shift)).map { this.stepBy(it) }
    return shifted.filterNot { it.isAfterCurrentPeriod() }.takeLast(5)
}

/**
 * Format a TimeFilter as a short label for chart axis display.
 */
fun TimeFilter.toChartLabel(): String {
    return when (this) {
        is TimeFilter.Day -> {
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            SimpleDateFormat("dd MMM", Locale.getDefault()).format(cal.time)
        }
        is TimeFilter.Week -> {
            val cal = Calendar.getInstance().apply { timeInMillis = weekStartMillis }
            val start = SimpleDateFormat("dd MMM", Locale.getDefault()).format(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 6)
            val end = SimpleDateFormat("dd MMM", Locale.getDefault()).format(cal.time)
            "$start-$end"
        }
        is TimeFilter.Month -> {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
            }
            SimpleDateFormat("MMM yy", Locale.getDefault()).format(cal.time)
        }
        is TimeFilter.Year -> year.toString()
        else -> ""
    }
}
