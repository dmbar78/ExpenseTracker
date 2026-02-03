package com.example.expensetracker.domain.usecase

import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.FilterPreferences
import com.example.expensetracker.data.FilterState
import com.example.expensetracker.data.TimeFilter
import com.example.expensetracker.data.TransferHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Calendar

/**
 * Unit tests for FilterUseCase.
 * Tests filter logic for expenses, incomes, and transfers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner.Silent::class)
class FilterUseCaseTest {

    @Mock
    private lateinit var filterPreferences: FilterPreferences

    private lateinit var filterUseCase: FilterUseCase

    private val today = System.currentTimeMillis()
    private val yesterday = today - 24 * 60 * 60 * 1000
    private val twoDaysAgo = today - 2 * 24 * 60 * 60 * 1000

    // Sample expenses for testing
    private val sampleExpenses = listOf(
        Expense(
            id = 1,
            account = "Bank Account",
            amount = BigDecimal("100.00"),
            currency = "USD",
            category = "Food",
            expenseDate = today,
            type = "Expense",
            comment = "Grocery shopping"
        ),
        Expense(
            id = 2,
            account = "Cash",
            amount = BigDecimal("50.00"),
            currency = "USD",
            category = "Transport",
            expenseDate = yesterday,
            type = "Expense",
            comment = "Bus ticket"
        ),
        Expense(
            id = 3,
            account = "Bank Account",
            amount = BigDecimal("200.00"),
            currency = "EUR",
            category = "Food",
            expenseDate = twoDaysAgo,
            type = "Expense",
            comment = "Restaurant dinner"
        )
    )

    // Sample transfers for testing
    private val sampleTransfers = listOf(
        TransferHistory(
            id = 1,
            sourceAccount = "Bank Account",
            destinationAccount = "Cash",
            amount = BigDecimal("100.00"),
            currency = "USD",
            date = today,
            comment = "ATM withdrawal"
        ),
        TransferHistory(
            id = 2,
            sourceAccount = "Cash",
            destinationAccount = "Savings",
            amount = BigDecimal("50.00"),
            currency = "USD",
            date = yesterday,
            comment = "Weekly savings"
        )
    )

    // Sample keyword map
    private val keywordNamesMap = mapOf(
        1 to listOf("groceries", "weekly"),
        2 to listOf("travel"),
        3 to listOf("dining", "weekend")
    )

    @Before
    fun setup() {
        whenever(filterPreferences.filterState).thenReturn(MutableStateFlow(FilterState()))
        filterUseCase = FilterUseCase(filterPreferences)
    }

    // ========== Expense Filter Tests ==========

    @Test
    fun `applyExpenseFilters returns all expenses when no filters active`() {
        val filter = FilterState()
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertEquals(3, result.size)
    }

    @Test
    fun `applyExpenseFilters filters by account correctly`() {
        val filter = FilterState(expenseIncomeAccount = "Bank Account")
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertEquals(2, result.size)
        assertTrue(result.all { it.account.equals("Bank Account", ignoreCase = true) })
    }

    @Test
    fun `applyExpenseFilters filters by category correctly`() {
        val filter = FilterState(category = "Food")
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertEquals(2, result.size)
        assertTrue(result.all { it.category.equals("Food", ignoreCase = true) })
    }

    @Test
    fun `applyExpenseFilters filters by text query in comment`() {
        val filter = FilterState(textQuery = "bus")
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertEquals(1, result.size)
        assertEquals(2, result.first().id)
    }

    @Test
    fun `applyExpenseFilters filters by text query in keywords`() {
        val filter = FilterState(textQuery = "groceries")
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertEquals(1, result.size)
        assertEquals(1, result.first().id)
    }

    @Test
    fun `applyExpenseFilters text query matches both comment and keywords`() {
        // "travel" is a keyword for expense 2
        val filter = FilterState(textQuery = "travel")
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertEquals(1, result.size)
        assertEquals(2, result.first().id)
    }

    @Test
    fun `applyExpenseFilters combines multiple filters correctly`() {
        val filter = FilterState(
            expenseIncomeAccount = "Bank Account",
            category = "Food"
        )
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertEquals(2, result.size)
    }

    @Test
    fun `applyExpenseFilters returns empty list when no matches`() {
        val filter = FilterState(category = "NonExistentCategory")
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `applyExpenseFilters is case insensitive for account`() {
        val filter = FilterState(expenseIncomeAccount = "bank account")
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertEquals(2, result.size)
    }

    @Test
    fun `applyExpenseFilters is case insensitive for category`() {
        val filter = FilterState(category = "food")
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertEquals(2, result.size)
    }

    @Test
    fun `applyExpenseFilters is case insensitive for text query`() {
        val filter = FilterState(textQuery = "GROCERY")
        val result = filterUseCase.applyExpenseFilters(sampleExpenses, filter, keywordNamesMap)
        assertEquals(1, result.size)
    }

    // ========== Transfer Filter Tests ==========

    @Test
    fun `applyTransferFilters returns all transfers when no filters active`() {
        val filter = FilterState()
        val result = filterUseCase.applyTransferFilters(sampleTransfers, filter)
        assertEquals(2, result.size)
    }

    @Test
    fun `applyTransferFilters filters by source account correctly`() {
        val filter = FilterState(transferSourceAccount = "Bank Account")
        val result = filterUseCase.applyTransferFilters(sampleTransfers, filter)
        assertEquals(1, result.size)
        assertEquals("Bank Account", result.first().sourceAccount)
    }

    @Test
    fun `applyTransferFilters filters by destination account correctly`() {
        val filter = FilterState(transferDestAccount = "Cash")
        val result = filterUseCase.applyTransferFilters(sampleTransfers, filter)
        assertEquals(1, result.size)
        assertEquals("Cash", result.first().destinationAccount)
    }

    @Test
    fun `applyTransferFilters filters by text query in comment`() {
        val filter = FilterState(textQuery = "ATM")
        val result = filterUseCase.applyTransferFilters(sampleTransfers, filter)
        assertEquals(1, result.size)
        assertEquals(1, result.first().id)
    }

    @Test
    fun `applyTransferFilters combines source and destination filters`() {
        val filter = FilterState(
            transferSourceAccount = "Cash",
            transferDestAccount = "Savings"
        )
        val result = filterUseCase.applyTransferFilters(sampleTransfers, filter)
        assertEquals(1, result.size)
        assertEquals(2, result.first().id)
    }

    @Test
    fun `applyTransferFilters returns empty list when no matches`() {
        val filter = FilterState(transferSourceAccount = "NonExistentAccount")
        val result = filterUseCase.applyTransferFilters(sampleTransfers, filter)
        assertTrue(result.isEmpty())
    }

    // ========== Filter State Update Tests ==========

    @Test
    fun `setTimeFilter updates filter state`() = runTest {
        val dayFilter = TimeFilter.Day(today)
        filterUseCase.setTimeFilter(dayFilter)
        assertEquals(dayFilter, filterUseCase.filterState.value.timeFilter)
    }

    @Test
    fun `setExpenseIncomeAccountFilter updates filter state`() = runTest {
        filterUseCase.setExpenseIncomeAccountFilter("TestAccount")
        assertEquals("TestAccount", filterUseCase.filterState.value.expenseIncomeAccount)
    }

    @Test
    fun `setCategoryFilter updates filter state`() = runTest {
        filterUseCase.setCategoryFilter("TestCategory")
        assertEquals("TestCategory", filterUseCase.filterState.value.category)
    }

    @Test
    fun `setTransferAccountFilters updates both source and destination`() = runTest {
        filterUseCase.setTransferAccountFilters("Source", "Dest")
        assertEquals("Source", filterUseCase.filterState.value.transferSourceAccount)
        assertEquals("Dest", filterUseCase.filterState.value.transferDestAccount)
    }

    @Test
    fun `resetAllFilters clears all filter values`() = runTest {
        // Set some filters first
        filterUseCase.setExpenseIncomeAccountFilter("TestAccount")
        filterUseCase.setCategoryFilter("TestCategory")
        filterUseCase.setTimeFilter(TimeFilter.Day(today))
        
        // Reset
        filterUseCase.resetAllFilters()
        
        val state = filterUseCase.filterState.value
        assertEquals(TimeFilter.None, state.timeFilter)
        assertNull(state.expenseIncomeAccount)
        assertNull(state.category)
        assertNull(state.textQuery)
        assertNull(state.transferSourceAccount)
        assertNull(state.transferDestAccount)
    }

    @Test
    fun `resetTimeFilter only clears time filter`() = runTest {
        // Set multiple filters
        filterUseCase.setExpenseIncomeAccountFilter("TestAccount")
        filterUseCase.setTimeFilter(TimeFilter.Day(today))
        
        // Reset only time filter
        filterUseCase.resetTimeFilter()
        
        val state = filterUseCase.filterState.value
        assertEquals(TimeFilter.None, state.timeFilter)
        assertEquals("TestAccount", state.expenseIncomeAccount) // Still set
    }
}
