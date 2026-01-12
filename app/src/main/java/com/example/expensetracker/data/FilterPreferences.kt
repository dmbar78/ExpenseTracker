package com.example.expensetracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.filterDataStore: DataStore<Preferences> by preferencesDataStore(name = "filter_preferences")

/**
 * Manages persistence of filter state using DataStore Preferences.
 */
class FilterPreferences(private val context: Context) {
    
    companion object {
        // Time filter keys
        private val TIME_FILTER_TYPE = stringPreferencesKey("time_filter_type")
        private val TIME_FILTER_VALUE1 = longPreferencesKey("time_filter_value1")
        private val TIME_FILTER_VALUE2 = longPreferencesKey("time_filter_value2")
        private val TIME_FILTER_INT1 = intPreferencesKey("time_filter_int1")
        private val TIME_FILTER_INT2 = intPreferencesKey("time_filter_int2")
        
        // Account/Category filter keys
        private val EXPENSE_INCOME_ACCOUNT = stringPreferencesKey("expense_income_account")
        private val CATEGORY = stringPreferencesKey("category")
        private val TRANSFER_SOURCE_ACCOUNT = stringPreferencesKey("transfer_source_account")
        private val TRANSFER_DEST_ACCOUNT = stringPreferencesKey("transfer_dest_account")
        
        // Time filter type values
        private const val TYPE_NONE = "none"
        private const val TYPE_DAY = "day"
        private const val TYPE_WEEK = "week"
        private const val TYPE_MONTH = "month"
        private const val TYPE_YEAR = "year"
        private const val TYPE_PERIOD = "period"
        private const val TYPE_ALL_TIME = "all_time"
    }
    
    /**
     * Flow of the current filter state from DataStore.
     */
    val filterState: Flow<FilterState> = context.filterDataStore.data.map { prefs ->
        val timeFilter = when (prefs[TIME_FILTER_TYPE]) {
            TYPE_DAY -> TimeFilter.Day(prefs[TIME_FILTER_VALUE1] ?: System.currentTimeMillis())
            TYPE_WEEK -> TimeFilter.Week(prefs[TIME_FILTER_VALUE1] ?: getWeekStartMillis(System.currentTimeMillis()))
            TYPE_MONTH -> TimeFilter.Month(
                prefs[TIME_FILTER_INT1] ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                prefs[TIME_FILTER_INT2] ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
            )
            TYPE_YEAR -> TimeFilter.Year(prefs[TIME_FILTER_INT1] ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
            TYPE_PERIOD -> TimeFilter.Period(
                prefs[TIME_FILTER_VALUE1] ?: System.currentTimeMillis(),
                prefs[TIME_FILTER_VALUE2] ?: System.currentTimeMillis()
            )
            TYPE_ALL_TIME -> TimeFilter.AllTime
            else -> TimeFilter.None
        }
        
        FilterState(
            timeFilter = timeFilter,
            expenseIncomeAccount = prefs[EXPENSE_INCOME_ACCOUNT],
            category = prefs[CATEGORY],
            transferSourceAccount = prefs[TRANSFER_SOURCE_ACCOUNT],
            transferDestAccount = prefs[TRANSFER_DEST_ACCOUNT]
        )
    }
    
    /**
     * Save the full filter state to DataStore.
     */
    suspend fun saveFilterState(state: FilterState) {
        context.filterDataStore.edit { prefs ->
            // Save time filter
            when (val tf = state.timeFilter) {
                is TimeFilter.None -> {
                    prefs[TIME_FILTER_TYPE] = TYPE_NONE
                    prefs.remove(TIME_FILTER_VALUE1)
                    prefs.remove(TIME_FILTER_VALUE2)
                    prefs.remove(TIME_FILTER_INT1)
                    prefs.remove(TIME_FILTER_INT2)
                }
                is TimeFilter.Day -> {
                    prefs[TIME_FILTER_TYPE] = TYPE_DAY
                    prefs[TIME_FILTER_VALUE1] = tf.dateMillis
                }
                is TimeFilter.Week -> {
                    prefs[TIME_FILTER_TYPE] = TYPE_WEEK
                    prefs[TIME_FILTER_VALUE1] = tf.weekStartMillis
                }
                is TimeFilter.Month -> {
                    prefs[TIME_FILTER_TYPE] = TYPE_MONTH
                    prefs[TIME_FILTER_INT1] = tf.year
                    prefs[TIME_FILTER_INT2] = tf.month
                }
                is TimeFilter.Year -> {
                    prefs[TIME_FILTER_TYPE] = TYPE_YEAR
                    prefs[TIME_FILTER_INT1] = tf.year
                }
                is TimeFilter.Period -> {
                    prefs[TIME_FILTER_TYPE] = TYPE_PERIOD
                    prefs[TIME_FILTER_VALUE1] = tf.startMillis
                    prefs[TIME_FILTER_VALUE2] = tf.endMillis
                }
                is TimeFilter.AllTime -> {
                    prefs[TIME_FILTER_TYPE] = TYPE_ALL_TIME
                }
            }
            
            // Save account/category filters (null removes the key)
            if (state.expenseIncomeAccount != null) {
                prefs[EXPENSE_INCOME_ACCOUNT] = state.expenseIncomeAccount
            } else {
                prefs.remove(EXPENSE_INCOME_ACCOUNT)
            }
            
            if (state.category != null) {
                prefs[CATEGORY] = state.category
            } else {
                prefs.remove(CATEGORY)
            }
            
            if (state.transferSourceAccount != null) {
                prefs[TRANSFER_SOURCE_ACCOUNT] = state.transferSourceAccount
            } else {
                prefs.remove(TRANSFER_SOURCE_ACCOUNT)
            }
            
            if (state.transferDestAccount != null) {
                prefs[TRANSFER_DEST_ACCOUNT] = state.transferDestAccount
            } else {
                prefs.remove(TRANSFER_DEST_ACCOUNT)
            }
        }
    }
    
    /**
     * Clear all filters.
     */
    suspend fun clearAllFilters() {
        saveFilterState(FilterState())
    }
}
