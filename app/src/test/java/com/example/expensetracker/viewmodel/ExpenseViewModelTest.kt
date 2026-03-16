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
        whenever(userPreferences.isGeminiEnabled).thenReturn(MutableStateFlow(false))
        whenever(userPreferences.geminiApiKey).thenReturn(MutableStateFlow(""))
        whenever(userPreferences.geminiModel).thenReturn(MutableStateFlow(UserPreferences.DEFAULT_GEMINI_MODEL))
        whenever(userPreferences.overrideDefaultAccountWithFilter).thenReturn(MutableStateFlow(false))
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

    @Test
    fun copyExpense_verifiesClonedState() = runTest {
        // GIVEN
        val sourceId = 100
        val originalDate = 1000L
        val sourceExpense = Expense(
            id = sourceId,
            amount = BigDecimal("50.00"),
            account = "Bank",
            category = "Food",
            currency = "USD",
            expenseDate = originalDate,
            type = "Expense",
            comment = "Lunch",
            relatedDebtId = 55, // Should be null in copy
            photoUri = "file:///some/old/photo.jpg" // Should be null in copy
        )
        val sourceKeywords = listOf(1, 2, 3)

        whenever(expenseRepository.getExpenseByIdOnce(sourceId)).thenReturn(sourceExpense)
        whenever(keywordDao.getKeywordIdsForExpense(sourceId)).thenReturn(sourceKeywords)
        
        // Use a background collector to keep the StateFlow active
        val results = mutableListOf<Expense?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.selectedExpense.collect { results.add(it) }
        }
        
        // Additional collector for keywords
        val keywordResults = mutableListOf<Pair<Int?, Set<Int>>>()
        val keywordJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.selectedExpenseKeywords.collect { keywordResults.add(it) }
        }

        // WHEN
        viewModel.loadExpense(0, copyFromId = sourceId)
        advanceUntilIdle()

        // Capture emissions (get the last non-null if possible, or just the last)
        // results might contain [null, clonedExpense] or just [null] if failed
        val clonedExpense = results.lastOrNull { it != null }
        val clonedKeywordPair = keywordResults.lastOrNull { it.second.isNotEmpty() } ?: keywordResults.lastOrNull()
        
        assertNotNull("Cloned expense should have been emitted", clonedExpense)

        // THEN
        // Identify check: ID should be 0 (ready for new insert)
        assertEquals("ID should be 0", 0, clonedExpense!!.id)
        
        // Value checks: Should match original
        assertEquals("Amount should match", sourceExpense.amount, clonedExpense.amount)
        assertEquals("Account should match", sourceExpense.account, clonedExpense.account)
        assertEquals("Category should match", sourceExpense.category, clonedExpense.category)
        assertEquals("Currency should match", sourceExpense.currency, clonedExpense.currency)
        assertEquals("Type should match", sourceExpense.type, clonedExpense.type)
        assertEquals("Comment should match", sourceExpense.comment, clonedExpense.comment)

        // Date check: Should NOT match original (should be recent)
        assertNotEquals("Date should be reset", originalDate, clonedExpense.expenseDate)
        assertTrue("Date should be recent", clonedExpense.expenseDate > originalDate)

        // Debt check: Should be reset
        assertNull("Related Debt ID should be null (unchecked)", clonedExpense.relatedDebtId)
        
        // Photo check: Should be reset
        assertNull("Photo URI should be null", clonedExpense.photoUri)
        
        // Keyword check
        assertEquals("Cloned expense ID should be 0", 0, clonedKeywordPair?.first)
        assertEquals("Keywords should be copied", sourceKeywords.toSet(), clonedKeywordPair?.second)
        
        job.cancel()
        keywordJob.cancel()
    }

    @Test
    fun copyTransfer_verifiesClonedState() = runTest {
        // GIVEN
        val sourceId = 200
        val originalDate = 2000L
        val sourceTransfer = TransferHistory(
            id = sourceId,
            sourceAccount = "Bank",
            destinationAccount = "Cash",
            amount = BigDecimal("100.00"),
            currency = "USD",
            date = originalDate,
            comment = "Withdrawal",
            destinationAmount = BigDecimal("100.00"),
            destinationCurrency = "USD"
        )
        
        whenever(transferHistoryRepository.getTransferByIdOnce(sourceId)).thenReturn(sourceTransfer)

        // Use a background collector
        val results = mutableListOf<TransferHistory?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.selectedTransfer.collect { results.add(it) }
        }

        // WHEN
        viewModel.loadTransfer(0, copyFromId = sourceId)
        advanceUntilIdle()
        
        // Capture emissions
        val clonedTransfer = results.lastOrNull { it != null }

        // THEN
        assertNotNull("Cloned transfer should have been emitted", clonedTransfer)
        
        // Identity check
        assertEquals("ID should be 0", 0, clonedTransfer!!.id)
        
        // Value checks
        assertEquals("Source Account should match", sourceTransfer.sourceAccount, clonedTransfer.sourceAccount)
        assertEquals("Dest Account should match", sourceTransfer.destinationAccount, clonedTransfer.destinationAccount)
        assertEquals("Amount should match", sourceTransfer.amount, clonedTransfer.amount)
        assertEquals("Currency should match", sourceTransfer.currency, clonedTransfer.currency)
        assertEquals("Comment should match", sourceTransfer.comment, clonedTransfer.comment)
        
        // Date check
        assertNotEquals("Date should be reset", originalDate, clonedTransfer.date)
        assertTrue("Date should be recent", clonedTransfer.date > originalDate)
        
        job.cancel()
    }

    @Test
    fun insertExpense_withLocalPhoto_savesSuccessfully() = runTest {
        // GIVEN
        val expense = Expense(
            id = 0,
            amount = BigDecimal("50.00"),
            account = "Bank",
            category = "Food",
            currency = "USD",
            expenseDate = 1000L,
            type = "Expense",
            photoUri = "file:///internal/app/images/img.jpg"
        )
        val keywords = setOf(1, 2)
        whenever(ledgerRepository.addExpense(org.mockito.kotlin.any())).thenReturn(101L)

        // WHEN
        val result = viewModel.insertExpenseWithKeywordsAndReturn(expense, keywords)
        advanceUntilIdle()

        // THEN
        assertTrue(result.isSuccess)
        assertEquals(101L, result.getOrNull())
        
        val captor = org.mockito.kotlin.argumentCaptor<Expense>()
        verify(ledgerRepository).addExpense(captor.capture())
        assertEquals("file:///internal/app/images/img.jpg", captor.firstValue.photoUri)
        verify(keywordDao).setKeywordsForExpense(101, keywords)
    }

    @Test
    fun updateExpense_withNewLocalPhoto_savesSuccessfully() = runTest {
        // GIVEN
        val existingExpense = Expense(
            id = 1,
            amount = BigDecimal("50.00"),
            account = "Bank",
            category = "Food",
            currency = "USD",
            expenseDate = 1000L,
            type = "Expense",
            photoUri = "file:///internal/app/images/old.jpg"
        )
        whenever(expenseRepository.getExpenseByIdOnce(1)).thenReturn(existingExpense)
        
        val updatedExpense = existingExpense.copy(photoUri = "file:///internal/app/images/new.jpg")
        val keywords = setOf(1)
        
        // WHEN
        val result = viewModel.updateExpenseWithKeywordsAndReturn(updatedExpense, keywords)
        advanceUntilIdle()

        // THEN
        assertTrue(result.isSuccess)
        val captor = org.mockito.kotlin.argumentCaptor<Expense>()
        verify(ledgerRepository).updateExpense(captor.capture())
        assertEquals("file:///internal/app/images/new.jpg", captor.firstValue.photoUri)
        verify(keywordDao).setKeywordsForExpense(1, keywords)
    }

    @Test
    fun updateExpense_deleteLocalPhoto_savesSuccessfully() = runTest {
        // GIVEN
        val existingExpense = Expense(
            id = 2,
            amount = BigDecimal("50.00"),
            account = "Bank",
            category = "Food",
            currency = "USD",
            expenseDate = 1000L,
            type = "Expense",
            photoUri = "file:///internal/app/images/old.jpg"
        )
        whenever(expenseRepository.getExpenseByIdOnce(2)).thenReturn(existingExpense)
        
        val updatedExpense = existingExpense.copy(photoUri = null)
        val keywords = setOf(1, 2)
        
        // WHEN
        val result = viewModel.updateExpenseWithKeywordsAndReturn(updatedExpense, keywords)
        advanceUntilIdle()

        // THEN
        assertTrue(result.isSuccess)
        val captor = org.mockito.kotlin.argumentCaptor<Expense>()
        verify(ledgerRepository).updateExpense(captor.capture())
        assertNull(captor.firstValue.photoUri)
        verify(keywordDao).setKeywordsForExpense(2, keywords)
    }

    @Test
    fun voiceParse_fallsBackToDefaultAccount_whenOverrideIsFalse() = runTest {
        org.mockito.Mockito.mockStatic(android.net.Uri::class.java).use { mockedUri ->
            mockedUri.`when`<String> { android.net.Uri.encode(org.mockito.kotlin.any()) }.thenAnswer { it.getArgument(0) }
            
            // GIVEN
            val defaultAccount = Account(id = 1, name = "Cash", currency = "USD", balance = BigDecimal.ZERO)
            val category = Category(id = 1, name = "Food")
            whenever(accountRepository.allAccounts).thenReturn(MutableStateFlow(listOf(defaultAccount)))
            whenever(categoryRepository.allCategories).thenReturn(MutableStateFlow(listOf(category)))
            whenever(userPreferences.isGeminiEnabled).thenReturn(MutableStateFlow(false))
            whenever(userPreferences.defaultExpenseAccountId).thenReturn(MutableStateFlow(1)) // Valid default
            whenever(userPreferences.overrideDefaultAccountWithFilter).thenReturn(MutableStateFlow(false))

            viewModel = ExpenseViewModel(
                application, expenseRepository, accountRepository, categoryRepository, currencyRepository,
                transferHistoryRepository, ledgerRepository, filterPreferences, userPreferences,
                exchangeRateRepository, backupRepository, keywordDao, debtRepository
            )
            val dummyJob = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allAccounts.collect { }
            }
            advanceUntilIdle() // let init load data and state flows initialize

            val navigationIntents = mutableListOf<String>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.navigateToFlow.collect { navigationIntents.add(it) }
            }

            // WHEN
            // "UnknownAccount" is not in the repository.
            viewModel.onVoiceRecognitionResult("expense from UnknownAccount 50 category Food")
            advanceUntilIdle()

            // THEN
            // Since inputAccount is null, and default exists, it should fall back to Default Account ("Cash")
            // and navigate to Edit screen because defaultAccountUsed = true.
            verify(ledgerRepository, never()).addExpense(org.mockito.kotlin.any())
            
            assertEquals(1, navigationIntents.size)
            val intent = navigationIntents.first()
            assertTrue(intent.contains("editExpense/0"))
            assertTrue("Account error should be false", intent.contains("accountError=false"))
            assertTrue("Category error should be false", intent.contains("categoryError=false"))
            assertTrue("Default account used should be true", intent.contains("defaultAccountUsed=true"))
            assertTrue("Should prefill with default account", intent.contains("accountName=Cash"))
            
            job.cancel()
            dummyJob.cancel()
        }
    }

    @Test
    fun voiceParse_fallsBackToFilterAccount_whenOverrideIsTrueAndFilterIsSet() = runTest {
        org.mockito.Mockito.mockStatic(android.net.Uri::class.java).use { mockedUri ->
            mockedUri.`when`<String> { android.net.Uri.encode(org.mockito.kotlin.any()) }.thenAnswer { it.getArgument(0) }
            
            // GIVEN
            val defaultAccount = Account(id = 1, name = "Cash", currency = "USD", balance = BigDecimal.ZERO)
            val filterAccount = Account(id = 2, name = "Bank", currency = "USD", balance = BigDecimal.ZERO)
            val category = Category(id = 1, name = "Food")
            
            whenever(accountRepository.allAccounts).thenReturn(MutableStateFlow(listOf(defaultAccount, filterAccount)))
            whenever(categoryRepository.allCategories).thenReturn(MutableStateFlow(listOf(category)))
            whenever(userPreferences.isGeminiEnabled).thenReturn(MutableStateFlow(false))
            whenever(userPreferences.defaultExpenseAccountId).thenReturn(MutableStateFlow(1))
            whenever(userPreferences.overrideDefaultAccountWithFilter).thenReturn(MutableStateFlow(true)) // Override active
            whenever(filterPreferences.filterState).thenReturn(MutableStateFlow(FilterState(expenseIncomeAccount = "Bank"))) // Filter active

            viewModel = ExpenseViewModel(
                application, expenseRepository, accountRepository, categoryRepository, currencyRepository,
                transferHistoryRepository, ledgerRepository, filterPreferences, userPreferences,
                exchangeRateRepository, backupRepository, keywordDao, debtRepository
            )
            val dummyJob = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allAccounts.collect { }
            }
            advanceUntilIdle() // let init load data and state flows initialize

            val navigationIntents = mutableListOf<String>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.navigateToFlow.collect { navigationIntents.add(it) }
            }

            // WHEN
            viewModel.onVoiceRecognitionResult("expense from UnknownAccount 50 category Food")
            advanceUntilIdle()

            // THEN
            // Should fall back to "Bank" from the filter state, ignoring the default "Cash"
            // and navigate to Edit screen.
            verify(ledgerRepository, never()).addExpense(org.mockito.kotlin.any())
            
            assertEquals(1, navigationIntents.size)
            val intent = navigationIntents.first()
            assertTrue(intent.contains("editExpense/0"))
            assertTrue("Account error should be false", intent.contains("accountError=false"))
            assertTrue("Category error should be false", intent.contains("categoryError=false"))
            assertTrue("Default account used should be true", intent.contains("defaultAccountUsed=true"))
            assertTrue("Should prefill with filter account", intent.contains("accountName=Bank"))
            
            job.cancel()
            dummyJob.cancel()
        }
    }

    @Test
    fun voiceParse_navigatesWithBlankAccount_whenOverrideIsFalseAndNoDefault() = runTest {
        org.mockito.Mockito.mockStatic(android.net.Uri::class.java).use { mockedUri ->
            mockedUri.`when`<String> { android.net.Uri.encode(org.mockito.kotlin.any()) }.thenAnswer { it.getArgument(0) }
            
            // GIVEN
            val category = Category(id = 1, name = "Food")
            whenever(accountRepository.allAccounts).thenReturn(MutableStateFlow(emptyList())) // No accounts match
            whenever(categoryRepository.allCategories).thenReturn(MutableStateFlow(listOf(category)))
            whenever(userPreferences.isGeminiEnabled).thenReturn(MutableStateFlow(false))
            whenever(userPreferences.defaultExpenseAccountId).thenReturn(MutableStateFlow(null)) // No default
            whenever(userPreferences.overrideDefaultAccountWithFilter).thenReturn(MutableStateFlow(false))

            viewModel = ExpenseViewModel(
                application, expenseRepository, accountRepository, categoryRepository, currencyRepository,
                transferHistoryRepository, ledgerRepository, filterPreferences, userPreferences,
                exchangeRateRepository, backupRepository, keywordDao, debtRepository
            )
            advanceUntilIdle()

            val navigationIntents = mutableListOf<String>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.navigateToFlow.collect { navigationIntents.add(it) }
            }

            // WHEN
            viewModel.onVoiceRecognitionResult("expense from UnknownAccount 50 category Food")
            advanceUntilIdle()

            // THEN
            // Since inputAccount is null, and default does not exist, it navigates with accountError=true
            verify(ledgerRepository, never()).addExpense(org.mockito.kotlin.any()) // No insert
            
            assertEquals(1, navigationIntents.size)
            val intent = navigationIntents.first()
            assertTrue(intent.contains("editExpense/0"))
            assertTrue("Account error should be true", intent.contains("accountError=true"))
            assertTrue("Category error should be false", intent.contains("categoryError=false"))
            assertTrue("Default account used should be false", intent.contains("defaultAccountUsed=false"))
            assertTrue("Original string should persist", intent.contains("accountName=UnknownAccount"))
            
            job.cancel()
        }
    }
}
