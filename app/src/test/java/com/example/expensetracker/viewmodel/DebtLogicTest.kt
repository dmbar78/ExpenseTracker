package com.example.expensetracker.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.expensetracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner.Silent::class)
class DebtLogicTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock private lateinit var application: Application
    @Mock private lateinit var expenseRepository: ExpenseRepository
    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var categoryRepository: CategoryRepository
    @Mock private lateinit var currencyRepository: CurrencyRepository
    @Mock private lateinit var transferHistoryRepository: TransferHistoryRepository
    @Mock private lateinit var ledgerRepository: LedgerRepository
    @Mock private lateinit var filterPreferences: FilterPreferences
    @Mock private lateinit var userPreferences: UserPreferences
    @Mock private lateinit var exchangeRateRepository: ExchangeRateRepository
    @Mock private lateinit var backupRepository: BackupRepository
    @Mock private lateinit var keywordDao: KeywordDao
    @Mock private lateinit var debtRepository: DebtRepository

    private lateinit var viewModel: ExpenseViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Default stubs
        whenever(accountRepository.allAccounts).thenReturn(MutableStateFlow(emptyList()))
        whenever(expenseRepository.allExpenses).thenReturn(MutableStateFlow(emptyList()))
        whenever(categoryRepository.allCategories).thenReturn(MutableStateFlow(emptyList()))
        whenever(currencyRepository.allCurrencies).thenReturn(MutableStateFlow(emptyList()))
        whenever(transferHistoryRepository.allTransfers).thenReturn(MutableStateFlow(emptyList()))
        whenever(keywordDao.getAllKeywords()).thenReturn(MutableStateFlow(emptyList()))
        whenever(keywordDao.getAllExpenseKeywordCrossRefs()).thenReturn(MutableStateFlow(emptyList()))
        whenever(debtRepository.getAllDebts()).thenReturn(MutableStateFlow(emptyList()))
        whenever(userPreferences.defaultCurrencyCode).thenReturn(MutableStateFlow("USD"))
        whenever(userPreferences.defaultExpenseAccountId).thenReturn(MutableStateFlow(null))
        whenever(userPreferences.defaultTransferAccountId).thenReturn(MutableStateFlow(null))
        whenever(filterPreferences.filterState).thenReturn(MutableStateFlow(FilterState()))
        whenever(expenseRepository.getExpensesByType(any())).thenReturn(MutableStateFlow(emptyList())) // Use any() from mockito-kotlin

        viewModel = ExpenseViewModel(
            application,
            expenseRepository,
            accountRepository,
            categoryRepository,
            currencyRepository,
            transferHistoryRepository,
            ledgerRepository,
            filterPreferences,
            userPreferences,
            exchangeRateRepository,
            backupRepository,
            keywordDao,
            debtRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `checkAndUpdateDebtStatus closes debt when fully paid`() = runTest {
        // GIVEN
        val debtId = 1
        val parentId = 100
        val debt = Debt(id = debtId, parentExpenseId = parentId, status = "OPEN", notes = null)
        val parentExpense = Expense(id = parentId, amount = BigDecimal("100"), currency = "USD", account = "Acc", category = "Cat", type = "Expense", expenseDate = 0L)
        
        val payment1 = Expense(id = 201, amount = BigDecimal("50"), currency = "USD", account = "Acc", category = "Cat", type = "Income", relatedDebtId = debtId, expenseDate = 0L)
        val payment2 = Expense(id = 202, amount = BigDecimal("50"), currency = "USD", account = "Acc", category = "Cat", type = "Income", relatedDebtId = debtId, expenseDate = 0L)
        val payments = listOf(payment1, payment2)

        whenever(debtRepository.getDebtById(debtId)).thenReturn(debt)
        whenever(expenseRepository.getExpenseById(parentId)).thenReturn(flowOf(parentExpense))
        whenever(expenseRepository.getExpensesByRelatedDebtId(debtId)).thenReturn(flowOf(payments))
        
        // WHEN
        viewModel.checkAndUpdateDebtStatus(debtId)
        advanceUntilIdle()

        // THEN
        // Use argumentCaptor from mockito-kotlin
        val captor = argumentCaptor<Debt>()
        verify(debtRepository).updateDebt(captor.capture())
        assertEquals("CLOSED", captor.lastValue.status)
    }

    @Test
    fun `checkAndUpdateDebtStatus keeps debt open when partially paid`() = runTest {
        // GIVEN
        val debtId = 2
        val parentId = 200
        val debt = Debt(id = debtId, parentExpenseId = parentId, status = "OPEN", notes = null)
        val parentExpense = Expense(id = parentId, amount = BigDecimal("100"), currency = "USD", account = "Acc", category = "Cat", type = "Expense", expenseDate = 0L)
        
        val payment1 = Expense(id = 301, amount = BigDecimal("50"), currency = "USD", account = "Acc", category = "Cat", type = "Income", relatedDebtId = debtId, expenseDate = 0L)
        val payments = listOf(payment1)

        whenever(debtRepository.getDebtById(debtId)).thenReturn(debt)
        whenever(expenseRepository.getExpenseById(parentId)).thenReturn(flowOf(parentExpense))
        whenever(expenseRepository.getExpensesByRelatedDebtId(debtId)).thenReturn(flowOf(payments))

        // WHEN
        viewModel.checkAndUpdateDebtStatus(debtId)
        advanceUntilIdle()

        // THEN
        // check that updateDebt was never called
        verify(debtRepository, never()).updateDebt(any())
    }

    @Test
    fun `checkAndUpdateDebtStatus reopens debt if payments removed`() = runTest {
        // GIVEN
        val debtId = 3
        val parentId = 300
        val debt = Debt(id = debtId, parentExpenseId = parentId, status = "CLOSED", notes = null)
        val parentExpense = Expense(id = parentId, amount = BigDecimal("100"), currency = "USD", account = "Acc", category = "Cat", type = "Expense", expenseDate = 0L)
        
        val payment1 = Expense(id = 401, amount = BigDecimal("20"), currency = "USD", account = "Acc", category = "Cat", type = "Income", relatedDebtId = debtId, expenseDate = 0L)
        val payments = listOf(payment1)

        whenever(debtRepository.getDebtById(debtId)).thenReturn(debt)
        whenever(expenseRepository.getExpenseById(parentId)).thenReturn(flowOf(parentExpense))
        whenever(expenseRepository.getExpensesByRelatedDebtId(debtId)).thenReturn(flowOf(payments))

        // WHEN
        viewModel.checkAndUpdateDebtStatus(debtId)
        advanceUntilIdle()

        // THEN
        val captor = argumentCaptor<Debt>()
        verify(debtRepository).updateDebt(captor.capture())
        assertEquals("OPEN", captor.lastValue.status)
    }
    
    @Test
    fun `calculateDebtPaidAmount handles currency conversion`() = runTest {
        // GIVEN
        val debtId = 4
        val debtCurrency = "USD"
        
        val payment1 = Expense(id = 501, amount = BigDecimal("100"), currency = "EUR", account = "Acc", category = "Cat", type = "Income", relatedDebtId = debtId, expenseDate = 1000L)
        val payments = listOf(payment1)
        
        whenever(expenseRepository.getExpensesByRelatedDebtId(debtId)).thenReturn(flowOf(payments))
        whenever(exchangeRateRepository.getMostRecentRateOnOrBefore("EUR", "USD", 1000L)).thenReturn(BigDecimal("1.1"))
        
        // WHEN
        val paidAmount = viewModel.calculateDebtPaidAmount(debtId, debtCurrency)
        
        // THEN
        assertEquals(0, BigDecimal("110.0").compareTo(paidAmount)) // Use compareTo for safety
    }

    // New Tests for Reported Bugs

    @Test
    fun `insertExpense triggers debt status check when relatedDebtId is present`() = runTest {
        // Test that simulates the bug: Logic to trigger checkAndUpdateDebtStatus is missing.
        // If we move logic to ViewModel (insertExpenseWithKeywordsAndReturn), this test should be green eventually.
        // Currently it should FAIL.
        
        // GIVEN
        val debtId = 10
        val payment = Expense(id = 0, amount = BigDecimal("50"), currency = "USD", account = "Acc", category = "Cat", type = "Income", relatedDebtId = debtId, expenseDate = 0L)
        val keywordIds = emptySet<Int>()
        
        whenever(ledgerRepository.addExpense(any())).thenReturn(999L) // Return dummy ID
        
        // WHEN
        viewModel.insertExpenseWithKeywordsAndReturn(payment, keywordIds)
        advanceUntilIdle()
        
        // THEN
        // We expect checkAndUpdateDebtStatus(debtId) to be called.
        // Note: verify(viewModel) won't work because viewModel is the SUT (system under test), not a mock.
        // We verify the SIDE EFFECT: debtRepository.getDebtById(debtId) is called.
        // (Since checkAndUpdateDebtStatus calls getDebtById first thing)
        
        verify(debtRepository).getDebtById(debtId)
    }

    @Test
    fun `getConvertedPaymentAmounts returns converted values respecting dates`() = runTest {
        // Test for the UI conversion display bug, ensuring it uses transaction date rates.
        
        // GIVEN
        val date1 = 1000L
        val date2 = 2000L
        val targetCurrency = "USD"
        
        // Payment 1: 100 EUR on date1. Rate 1.1 -> 110 USD.
        val payment1 = Expense(id = 1, amount = BigDecimal("100"), currency = "EUR", account = "Acc", category = "Cat", type = "Income", expenseDate = date1) 
        
        // Payment 2: 100 EUR on date2. Rate 1.2 -> 120 USD.
        val payment2 = Expense(id = 2, amount = BigDecimal("100"), currency = "EUR", account = "Acc", category = "Cat", type = "Income", expenseDate = date2)
        
        val payments = listOf(payment1, payment2)
        
        // Mock Rates
        whenever(exchangeRateRepository.getMostRecentRateOnOrBefore("EUR", "USD", date1)).thenReturn(BigDecimal("1.1"))
        whenever(exchangeRateRepository.getMostRecentRateOnOrBefore("EUR", "USD", date2)).thenReturn(BigDecimal("1.2"))
        
        // WHEN
        val result = viewModel.getConvertedPaymentAmounts(payments, targetCurrency)
        
        // THEN
        assertEquals(2, result.size)
        // Payment 1: 100 * 1.1 = 110.0
        assertEquals(0, BigDecimal("110.0").compareTo(result[1] ?: BigDecimal.ZERO))
        // Payment 2: 100 * 1.2 = 120.0
        assertEquals(0, BigDecimal("120.0").compareTo(result[2] ?: BigDecimal.ZERO))
    }
}
