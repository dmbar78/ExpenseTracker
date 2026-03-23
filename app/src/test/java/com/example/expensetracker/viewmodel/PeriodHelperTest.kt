package com.example.expensetracker.viewmodel

import com.example.expensetracker.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PeriodHelperTest {

    // ==================== toChartGrain ====================

    @Test
    fun toChartGrain_day() {
        val tf = TimeFilter.Day(System.currentTimeMillis())
        assertEquals(ChartGrain.Day, tf.toChartGrain())
    }

    @Test
    fun toChartGrain_week() {
        val tf = TimeFilter.Week(getWeekStartMillis(System.currentTimeMillis()))
        assertEquals(ChartGrain.Week, tf.toChartGrain())
    }

    @Test
    fun toChartGrain_month() {
        val tf = TimeFilter.Month(2026, Calendar.MARCH)
        assertEquals(ChartGrain.Month, tf.toChartGrain())
    }

    @Test
    fun toChartGrain_year() {
        val tf = TimeFilter.Year(2026)
        assertEquals(ChartGrain.Year, tf.toChartGrain())
    }

    @Test
    fun toChartGrain_none_returnsNull() {
        assertNull(TimeFilter.None.toChartGrain())
    }

    @Test
    fun toChartGrain_allTime_returnsNull() {
        assertNull(TimeFilter.AllTime.toChartGrain())
    }

    // ==================== stepBy ====================

    @Test
    fun stepBy_month_forward() {
        val jan = TimeFilter.Month(2026, Calendar.JANUARY)
        val feb = jan.stepBy(1) as TimeFilter.Month
        assertEquals(2026, feb.year)
        assertEquals(Calendar.FEBRUARY, feb.month)
    }

    @Test
    fun stepBy_month_backward() {
        val jan = TimeFilter.Month(2026, Calendar.JANUARY)
        val dec = jan.stepBy(-1) as TimeFilter.Month
        assertEquals(2025, dec.year)
        assertEquals(Calendar.DECEMBER, dec.month)
    }

    @Test
    fun stepBy_year_forward() {
        val y2026 = TimeFilter.Year(2026)
        val y2027 = y2026.stepBy(1) as TimeFilter.Year
        assertEquals(2027, y2027.year)
    }

    @Test
    fun stepBy_day_forward() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 23, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val day = TimeFilter.Day(cal.timeInMillis)
        val next = day.stepBy(1) as TimeFilter.Day
        val nextCal = Calendar.getInstance().apply { timeInMillis = next.dateMillis }
        assertEquals(24, nextCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun stepBy_week_forward() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 23, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val week = TimeFilter.Week(getWeekStartMillis(cal.timeInMillis))
        val next = week.stepBy(1) as TimeFilter.Week
        val expected = Calendar.getInstance().apply {
            timeInMillis = week.weekStartMillis
            add(Calendar.WEEK_OF_YEAR, 1)
        }
        assertEquals(getWeekStartMillis(expected.timeInMillis), next.weekStartMillis)
    }

    // ==================== isAfterCurrentPeriod ====================

    @Test
    fun isAfterCurrentPeriod_currentMonth_false() {
        val now = Calendar.getInstance()
        val tf = TimeFilter.Month(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
        assertFalse(tf.isAfterCurrentPeriod())
    }

    @Test
    fun isAfterCurrentPeriod_nextMonth_true() {
        val now = Calendar.getInstance()
        now.add(Calendar.MONTH, 1)
        val tf = TimeFilter.Month(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
        assertTrue(tf.isAfterCurrentPeriod())
    }

    @Test
    fun isAfterCurrentPeriod_nextYear_true() {
        val now = Calendar.getInstance()
        val tf = TimeFilter.Year(now.get(Calendar.YEAR) + 1)
        assertTrue(tf.isAfterCurrentPeriod())
    }

    // ==================== generate5PeriodWindow ====================

    @Test
    fun generate5PeriodWindow_month_pastPeriod_returns5() {
        // Jan 2025 should be safely in the past
        val tf = TimeFilter.Month(2025, Calendar.JANUARY)
        val window = tf.generate5PeriodWindow()
        assertEquals(5, window.size)
    }

    @Test
    fun generate5PeriodWindow_currentMonth_caps() {
        val now = Calendar.getInstance()
        val tf = TimeFilter.Month(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
        val window = tf.generate5PeriodWindow()
        assertTrue(window.size in 1..5)
        // No period should be after current
        window.forEach { assertFalse(it.isAfterCurrentPeriod()) }
    }

    @Test
    fun generate5PeriodWindow_year_noFuture() {
        val now = Calendar.getInstance()
        val tf = TimeFilter.Year(now.get(Calendar.YEAR))
        val window = tf.generate5PeriodWindow()
        window.forEach { assertFalse(it.isAfterCurrentPeriod()) }
    }

    // ==================== toChartLabel ====================

    @Test
    fun toChartLabel_month() {
        val tf = TimeFilter.Month(2026, Calendar.MARCH)
        val label = tf.toChartLabel()
        assertTrue(label.contains("Mar") || label.contains("3"))
        assertTrue(label.contains("26"))
    }

    @Test
    fun toChartLabel_year() {
        val tf = TimeFilter.Year(2026)
        assertEquals("2026", tf.toChartLabel())
    }

    @Test
    fun toChartLabel_day() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 23, 0, 0, 0)
        }
        val tf = TimeFilter.Day(cal.timeInMillis)
        val label = tf.toChartLabel()
        assertTrue(label.contains("23"))
    }

    // ==================== chartGrainToCurrentTimeFilter ====================

    @Test
    fun chartGrainToCurrentTimeFilter_month() {
        val tf = chartGrainToCurrentTimeFilter(ChartGrain.Month) as TimeFilter.Month
        val now = Calendar.getInstance()
        assertEquals(now.get(Calendar.YEAR), tf.year)
        assertEquals(now.get(Calendar.MONTH), tf.month)
    }

    @Test
    fun chartGrainToCurrentTimeFilter_year() {
        val tf = chartGrainToCurrentTimeFilter(ChartGrain.Year) as TimeFilter.Year
        val now = Calendar.getInstance()
        assertEquals(now.get(Calendar.YEAR), tf.year)
    }
}
