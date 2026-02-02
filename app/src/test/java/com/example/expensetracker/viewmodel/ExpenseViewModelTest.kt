package com.example.expensetracker.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.expensetracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import java.math.BigDecimal
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

/**
 * Unit tests for ExpenseViewModel, focusing on voice recognition edge cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner.Silent::class)
class ExpenseViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var expenseRepository: ExpenseRepository

    @Mock
    private lateinit var accountRepository: AccountRepository

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @Mock
    private lateinit var currencyRepository: CurrencyRepository

    @Mock
    private lateinit var transferHistoryRepository: TransferHistoryRepository

    @Mock
    private lateinit var ledgerRepository: LedgerRepository

    @Mock
    private lateinit var filterPreferences: FilterPreferences

    @Mock
    private lateinit var userPreferences: UserPreferences

    @Mock
    private lateinit var exchangeRateRepository: ExchangeRateRepository

    @Mock
    private lateinit var backupRepository: BackupRepository

    @Mock
    private lateinit var keywordDao: KeywordDao

    @Mock
    private lateinit var debtRepository: DebtRepository

    private lateinit var viewModel: ExpenseViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup default returns for repositories
        whenever(accountRepository.allAccounts).thenReturn(MutableStateFlow(emptyList()))
        whenever(expenseRepository.allExpenses).thenReturn(MutableStateFlow(emptyList()))
        whenever(categoryRepository.allCategories).thenReturn(MutableStateFlow(emptyList()))
        whenever(currencyRepository.allCurrencies).thenReturn(MutableStateFlow(emptyList()))
        whenever(transferHistoryRepository.allTransfers).thenReturn(MutableStateFlow(emptyList()))
        whenever(keywordDao.getAllKeywords()).thenReturn(MutableStateFlow(emptyList()))
        whenever(keywordDao.getAllExpenseKeywordCrossRefs()).thenReturn(MutableStateFlow(emptyList()))
        whenever(userPreferences.defaultCurrencyCode).thenReturn(MutableStateFlow("USD"))
        whenever(userPreferences.defaultExpenseAccountId).thenReturn(MutableStateFlow(null))
        whenever(userPreferences.defaultTransferAccountId).thenReturn(MutableStateFlow(null))
        whenever(filterPreferences.filterState).thenReturn(MutableStateFlow(FilterState()))
        whenever(expenseRepository.getExpensesByType(anyString())).thenReturn(MutableStateFlow(emptyList()))
        whenever(debtRepository.getAllDebts()).thenReturn(MutableStateFlow(emptyList()))

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
    fun deleteAccount_blockedIfExpensesExist() = runTest {
        // GIVEN
        val account = Account(id = 1, name = "TestAccount", currency = "USD", balance = BigDecimal.ZERO)
        whenever(expenseRepository.getCountByAccount("TestAccount")).thenReturn(5)
        whenever(transferHistoryRepository.getCountByAccount("TestAccount")).thenReturn(0)

        // Capture emissions
        val errors = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errorFlow.collect { errors.add(it) }
        }

        // WHEN
        viewModel.deleteAccount(account)
        advanceUntilIdle()

        // THEN
        // Verify delete was NOT called
        verify(accountRepository, never()).delete(account)
        // Verify error message
        assert(errors.isNotEmpty())
        assert(errors.first() == "Cannot delete account. It has associated expenses or transfers.")
        
        job.cancel()
    }

    @Test
    fun deleteAccount_allowedIfClean() = runTest {
        // GIVEN
        val account = Account(id = 2, name = "CleanAccount", currency = "USD", balance = BigDecimal.ZERO)
        whenever(expenseRepository.getCountByAccount("CleanAccount")).thenReturn(0)
        whenever(transferHistoryRepository.getCountByAccount("CleanAccount")).thenReturn(0)

        // WHEN
        viewModel.deleteAccount(account)
        advanceUntilIdle()

        // THEN
        verify(accountRepository).delete(account)
    }

    @Test
    fun updateAccount_cascadesNameChange() = runTest {
        // GIVEN
        val oldAccount = Account(id = 1, name = "OldName", currency = "USD", balance = BigDecimal.ZERO)
        val updatedAccount = oldAccount.copy(name = "NewName")
        
        whenever(accountRepository.getAccountById(1)).thenReturn(MutableStateFlow(oldAccount))
        
        // WHEN
        viewModel.updateAccount(updatedAccount)
        advanceUntilIdle()

        // THEN
        verify(accountRepository).update(org.mockito.kotlin.argThat { name == "NewName" })
        verify(expenseRepository).updateAccountName("OldName", "NewName")
        verify(transferHistoryRepository).updateAccountName("OldName", "NewName")
    }

    @Test
    fun updateCategory_cascadesNameChange() = runTest {
        // GIVEN
        val oldCategory = Category(id = 10, name = "OldCat")
        val updatedCategory = oldCategory.copy(name = "NewCat")
        
        whenever(categoryRepository.getCategoryById(10)).thenReturn(MutableStateFlow(oldCategory))
        
        // WHEN
        viewModel.updateCategory(updatedCategory)
        advanceUntilIdle()

        // THEN
        verify(categoryRepository).update(org.mockito.kotlin.argThat { name == "NewCat" })
        verify(expenseRepository).updateCategoryName("OldCat", "NewCat")
    }
}
