package com.example.expensetracker.domain.usecase

import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.FilterPreferences
import com.example.expensetracker.data.FilterState
import com.example.expensetracker.data.TimeFilter
import com.example.expensetracker.data.TransferHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for managing filter operations.
 * 
 * Encapsulates business logic for:
 * - Applying filters to expenses, incomes, and transfers
 * - Persisting filter state
 * - Providing filtered data streams
 */
@Singleton
class FilterUseCase @Inject constructor(
    private val filterPreferences: FilterPreferences
) {
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    /**
     * Load persisted filter state from preferences.
     */
    val persistedFilterState: Flow<FilterState> = filterPreferences.filterState

    /**
     * Update the filter state (not persisted until explicitly saved).
     */
    fun updateFilterState(state: FilterState) {
        _filterState.value = state
    }

    /**
     * Apply filters to expenses/incomes based on current FilterState.
     * @param keywordNamesMap Map of expenseId to list of keyword names for text query matching
     */
    fun applyExpenseFilters(
        expenses: List<Expense>,
        filter: FilterState,
        keywordNamesMap: Map<Int, List<String>>
    ): List<Expense> {
        var result = expenses

        // Apply time filter
        val timeRange = filter.timeFilter.toDateRange()
        if (timeRange != null) {
            result = result.filter { it.expenseDate in timeRange.first..timeRange.second }
        }

        // Apply account filter
        filter.expenseIncomeAccount?.let { account ->
            result = result.filter { it.account.equals(account, ignoreCase = true) }
        }

        // Apply category filter
        filter.category?.let { category ->
            result = result.filter { it.category.equals(category, ignoreCase = true) }
        }

        // Apply text query filter (matches comment or any keyword name)
        filter.textQuery?.let { query ->
            val lowerQuery = query.lowercase()
            result = result.filter { expense ->
                val commentMatches = expense.comment?.lowercase()?.contains(lowerQuery) == true
                val keywordMatches = keywordNamesMap[expense.id]?.any { it.lowercase().contains(lowerQuery) } == true
                commentMatches || keywordMatches
            }
        }

        return result
    }

    /**
     * Apply filters to transfers based on current FilterState.
     */
    fun applyTransferFilters(transfers: List<TransferHistory>, filter: FilterState): List<TransferHistory> {
        var result = transfers

        // Apply time filter
        val timeRange = filter.timeFilter.toDateRange()
        if (timeRange != null) {
            result = result.filter { it.date in timeRange.first..timeRange.second }
        }

        // Apply source account filter
        filter.transferSourceAccount?.let { source ->
            result = result.filter { it.sourceAccount.equals(source, ignoreCase = true) }
        }

        // Apply destination account filter
        filter.transferDestAccount?.let { dest ->
            result = result.filter { it.destinationAccount.equals(dest, ignoreCase = true) }
        }

        // Apply text query filter (matches comment only - transfers have no keywords)
        filter.textQuery?.let { query ->
            val lowerQuery = query.lowercase()
            result = result.filter { transfer ->
                transfer.comment?.lowercase()?.contains(lowerQuery) == true
            }
        }

        return result
    }

    /**
     * Set the time filter and persist.
     */
    suspend fun setTimeFilter(timeFilter: TimeFilter) {
        val newState = _filterState.value.copy(timeFilter = timeFilter)
        _filterState.value = newState
        filterPreferences.saveFilterState(newState)
    }

    /**
     * Set the expense/income account filter and persist.
     */
    suspend fun setExpenseIncomeAccountFilter(account: String?) {
        val newState = _filterState.value.copy(expenseIncomeAccount = account)
        _filterState.value = newState
        filterPreferences.saveFilterState(newState)
    }

    /**
     * Set the category filter and persist.
     */
    suspend fun setCategoryFilter(category: String?) {
        val newState = _filterState.value.copy(category = category)
        _filterState.value = newState
        filterPreferences.saveFilterState(newState)
    }

    /**
     * Set the transfer source account filter and persist.
     */
    suspend fun setTransferSourceAccountFilter(account: String?) {
        val newState = _filterState.value.copy(transferSourceAccount = account)
        _filterState.value = newState
        filterPreferences.saveFilterState(newState)
    }

    /**
     * Set the transfer destination account filter and persist.
     */
    suspend fun setTransferDestAccountFilter(account: String?) {
        val newState = _filterState.value.copy(transferDestAccount = account)
        _filterState.value = newState
        filterPreferences.saveFilterState(newState)
    }

    /**
     * Set both transfer account filters at once and persist.
     */
    suspend fun setTransferAccountFilters(sourceAccount: String?, destAccount: String?) {
        val newState = _filterState.value.copy(
            transferSourceAccount = sourceAccount,
            transferDestAccount = destAccount
        )
        _filterState.value = newState
        filterPreferences.saveFilterState(newState)
    }

    /**
     * Set the text query filter and persist.
     */
    suspend fun setTextQueryFilter(query: String?) {
        val newState = _filterState.value.copy(textQuery = query)
        _filterState.value = newState
        filterPreferences.saveFilterState(newState)
    }

    /**
     * Reset all filters.
     */
    suspend fun resetAllFilters() {
        _filterState.value = FilterState()
        filterPreferences.clearAllFilters()
    }

    /**
     * Reset only the time filter.
     */
    suspend fun resetTimeFilter() {
        setTimeFilter(TimeFilter.None)
    }
}
