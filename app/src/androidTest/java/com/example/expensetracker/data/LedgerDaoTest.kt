package com.example.expensetracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Instrumented tests for LedgerDao transactional operations.
 * Uses an in-memory Room database for isolation and speed.
 * 
 * Covers flows 0-16 from the test plan at the DAO level.
 */
@RunWith(AndroidJUnit4::class)
class LedgerDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var ledgerDao: LedgerDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao

    // Test accounts
    private lateinit var test0: Account // RUB, balance 100
    private lateinit var test1: Account // EUR, balance 100
    private lateinit var test2: Account // EUR, balance 100
    private lateinit var test3: Account // EUR, balance 100

    // Test category
    private lateinit var defaultCategory: Category

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        ledgerDao = database.ledgerDao()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()

        // Flow 0: Create account Test0 with currency = RUB and balance 100
        test0 = Account(name = "Test0", currency = "RUB", balance = BigDecimal("100.00"))
        accountDao.insert(test0)
        test0 = ledgerDao.getAccountByNameOnce("Test0")!!

        // Flow 1: Create accounts Test1, Test2, Test3 with currency = EUR and balance 100
        test1 = Account(name = "Test1", currency = "EUR", balance = BigDecimal("100.00"))
        test2 = Account(name = "Test2", currency = "EUR", balance = BigDecimal("100.00"))
        test3 = Account(name = "Test3", currency = "EUR", balance = BigDecimal("100.00"))
        accountDao.insert(test1)
        accountDao.insert(test2)
        accountDao.insert(test3)
        test1 = ledgerDao.getAccountByNameOnce("Test1")!!
        test2 = ledgerDao.getAccountByNameOnce("Test2")!!
        test3 = ledgerDao.getAccountByNameOnce("Test3")!!

        // Create default category
        defaultCategory = Category(name = "Default")
        categoryDao.insert(defaultCategory)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 0-1: Account setup verification
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow0_accountTest0Created() = runBlocking {
        val account = ledgerDao.getAccountByNameOnce("Test0")
        assertNotNull(account)
        assertEquals("RUB", account!!.currency)
        assertEquals(BigDecimal("100.00"), account.balance)
    }

    @Test
    fun flow1_accountsTest1Test2Test3Created() = runBlocking {
        val t1 = ledgerDao.getAccountByNameOnce("Test1")
        val t2 = ledgerDao.getAccountByNameOnce("Test2")
        val t3 = ledgerDao.getAccountByNameOnce("Test3")
        
        assertNotNull(t1)
        assertNotNull(t2)
        assertNotNull(t3)
        
        assertEquals("EUR", t1!!.currency)
        assertEquals("EUR", t2!!.currency)
        assertEquals("EUR", t3!!.currency)
        
        assertEquals(BigDecimal("100.00"), t1.balance)
        assertEquals(BigDecimal("100.00"), t2.balance)
        assertEquals(BigDecimal("100.00"), t3.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 2: Create Expense from Test1, amount=20, category=default
    // Result: Test1 balance = 80 EUR
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow2_createExpenseFromTest1_balanceDecreasedBy20() = runBlocking {
        val expense = Expense(
            account = "Test1",
            amount = BigDecimal("20.00"),
            currency = "EUR",
            category = "Default",
            type = "Expense"
        )
        
        ledgerDao.addExpenseAndAdjust(expense)
        
        val updatedAccount = ledgerDao.getAccountByNameOnce("Test1")
        assertEquals(BigDecimal("80.00"), updatedAccount!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 4: Create Income to Test1, amount=20
    // Result: Test1 balance = 100 EUR (after flow 2's 80 + 20)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow4_createIncomeToTest1_balanceIncreasedBy20() = runBlocking {
        // First create the expense from flow 2
        val expense = Expense(
            account = "Test1",
            amount = BigDecimal("20.00"),
            currency = "EUR",
            category = "Default",
            type = "Expense"
        )
        ledgerDao.addExpenseAndAdjust(expense)
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        
        // Now create income
        val income = Expense(
            account = "Test1",
            amount = BigDecimal("20.00"),
            currency = "EUR",
            category = "Default",
            type = "Income"
        )
        ledgerDao.addExpenseAndAdjust(income)
        
        val updatedAccount = ledgerDao.getAccountByNameOnce("Test1")
        assertEquals(BigDecimal("100.00"), updatedAccount!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 6: Transfer from Test0 (RUB) to Test1 (EUR) - Multi-currency support
    // Result: Test0 balance = 80 RUB, Test1 balance = 105 EUR (assuming 20 RUB -> 5 EUR rate manually specified)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow6_transferDifferentCurrencies_success() = runBlocking {
        val transfer = TransferHistory(
            sourceAccount = "Test0",
            destinationAccount = "Test1",
            amount = BigDecimal("20.00"), // RUB
            currency = "RUB",
            destinationAmount = BigDecimal("5.00"), // EUR
            destinationCurrency = "EUR"
        )
        
        ledgerDao.addTransferAndAdjust(transfer)
        
        // Test0 (Source): 100 - 20 = 80 RUB
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test0")!!.balance)
        // Test1 (Dest): 100 + 5 = 105 EUR
        assertEquals(BigDecimal("105.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 7: Transfer from Test0 to Test0 - Same account error
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow7_transferSameAccount_throwsSameAccountError() = runBlocking {
        val transfer = TransferHistory(
            sourceAccount = "Test0",
            destinationAccount = "Test0",
            amount = BigDecimal("20.00"),
            currency = "RUB"
        )
        
        try {
            ledgerDao.addTransferAndAdjust(transfer)
            fail("Expected IllegalArgumentException for same account")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("same", ignoreCase = true))
        }
        
        // Balance should be unchanged
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test0")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 8: Transfer from Test1 to Test2 for 20 EUR
    // Result: Test1 balance = 80, Test2 balance = 120
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow8_transferTest1ToTest2_balancesAdjusted() = runBlocking {
        val transfer = TransferHistory(
            sourceAccount = "Test1",
            destinationAccount = "Test2",
            amount = BigDecimal("20.00"),
            currency = "EUR"
        )
        
        val transferId = ledgerDao.addTransferAndAdjust(transfer)
        assertTrue(transferId > 0)
        
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("120.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 9: Transfer from Test2 to Test1 for 20 EUR
    // Starting from flow 8 state: Test1=80, Test2=120
    // Result: Test1 balance = 100, Test2 balance = 100
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow9_transferTest2ToTest1_balancesEqualized() = runBlocking {
        // Setup: flow 8
        val transfer1 = TransferHistory(
            sourceAccount = "Test1",
            destinationAccount = "Test2",
            amount = BigDecimal("20.00"),
            currency = "EUR"
        )
        ledgerDao.addTransferAndAdjust(transfer1)
        
        // Flow 9: reverse transfer
        val transfer2 = TransferHistory(
            sourceAccount = "Test2",
            destinationAccount = "Test1",
            amount = BigDecimal("20.00"),
            currency = "EUR"
        )
        ledgerDao.addTransferAndAdjust(transfer2)
        
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 10: Delete transfer record - balances should revert
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow10_deleteTransfer_balancesReverted() = runBlocking {
        // Create a transfer
        val transfer = TransferHistory(
            sourceAccount = "Test1",
            destinationAccount = "Test2",
            amount = BigDecimal("20.00"),
            currency = "EUR"
        )
        val transferId = ledgerDao.addTransferAndAdjust(transfer)
        
        // Verify balances changed
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("120.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
        
        // Delete the transfer
        ledgerDao.deleteTransferAndAdjust(transferId.toInt())
        
        // Balances should be back to original
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 11: Update transfer - change amount
    // Original: Test1->Test2 for 20. Update to 30.
    // Result: Test1=70, Test2=130
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow11_updateTransferAmount_balancesAdjusted() = runBlocking {
        // Create original transfer
        val transfer = TransferHistory(
            sourceAccount = "Test1",
            destinationAccount = "Test2",
            amount = BigDecimal("20.00"),
            currency = "EUR"
        )
        val transferId = ledgerDao.addTransferAndAdjust(transfer)
        
        // Update amount to 30
        val updatedTransfer = ledgerDao.getTransferByIdOnce(transferId.toInt())!!
            .copy(amount = BigDecimal("30.00"))
        ledgerDao.updateTransferAndAdjust(updatedTransfer)
        
        assertEquals(BigDecimal("70.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("130.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 12: Update transfer - switch accounts
    // Original: Test1->Test2 for 20. Switch to Test2->Test1.
    // After original: Test1=80, Test2=120
    // After switch: Test1=120, Test2=80
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow12_updateTransferSwitchAccounts_balancesSwapped() = runBlocking {
        // Create original transfer
        val transfer = TransferHistory(
            sourceAccount = "Test1",
            destinationAccount = "Test2",
            amount = BigDecimal("20.00"),
            currency = "EUR"
        )
        val transferId = ledgerDao.addTransferAndAdjust(transfer)
        
        // Verify initial state
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("120.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
        
        // Switch accounts
        val updatedTransfer = ledgerDao.getTransferByIdOnce(transferId.toInt())!!
            .copy(sourceAccount = "Test2", destinationAccount = "Test1")
        ledgerDao.updateTransferAndAdjust(updatedTransfer)
        
        // Balances should be reversed
        assertEquals(BigDecimal("120.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 13: Update transfer - change destination account
    // Original: Test1->Test2 for 20. Change to Test1->Test3.
    // After original: Test1=80, Test2=120, Test3=100
    // After change: Test1=80, Test2=100, Test3=120
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow13_updateTransferChangeDestination_balancesAdjusted() = runBlocking {
        // Create original transfer
        val transfer = TransferHistory(
            sourceAccount = "Test1",
            destinationAccount = "Test2",
            amount = BigDecimal("20.00"),
            currency = "EUR"
        )
        val transferId = ledgerDao.addTransferAndAdjust(transfer)
        
        // Verify initial state
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("120.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test3")!!.balance)
        
        // Change destination to Test3
        val updatedTransfer = ledgerDao.getTransferByIdOnce(transferId.toInt())!!
            .copy(destinationAccount = "Test3")
        ledgerDao.updateTransferAndAdjust(updatedTransfer)
        
        // Test1 should stay at 80 (source unchanged)
        // Test2 should revert to 100 (no longer destination)
        // Test3 should become 120 (new destination)
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
        assertEquals(BigDecimal("120.00"), ledgerDao.getAccountByNameOnce("Test3")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 14: Update expense - change amount
    // Original: Expense from Test1 for 20. Update to 30.
    // After original: Test1=80
    // After update: Test1=70
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow14_updateExpenseAmount_balanceAdjusted() = runBlocking {
        // Create original expense
        val expense = Expense(
            account = "Test1",
            amount = BigDecimal("20.00"),
            currency = "EUR",
            category = "Default",
            type = "Expense"
        )
        val expenseId = ledgerDao.addExpenseAndAdjust(expense)
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        
        // Update amount to 30
        val updatedExpense = ledgerDao.getExpenseByIdOnce(expenseId.toInt())!!
            .copy(amount = BigDecimal("30.00"))
        ledgerDao.updateExpenseAndAdjust(updatedExpense)
        
        assertEquals(BigDecimal("70.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 15: Update expense - change account
    // Original: Expense from Test1 for 20. Change to Test2.
    // After original: Test1=80, Test2=100
    // After update: Test1=100, Test2=80
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow15_updateExpenseChangeAccount_balancesAdjusted() = runBlocking {
        // Create original expense
        val expense = Expense(
            account = "Test1",
            amount = BigDecimal("20.00"),
            currency = "EUR",
            category = "Default",
            type = "Expense"
        )
        val expenseId = ledgerDao.addExpenseAndAdjust(expense)
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
        
        // Change account to Test2
        val updatedExpense = ledgerDao.getExpenseByIdOnce(expenseId.toInt())!!
            .copy(account = "Test2", currency = "EUR")
        ledgerDao.updateExpenseAndAdjust(updatedExpense)
        
        // Test1 should revert to 100, Test2 should become 80
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test2")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flow 16: Delete expense - balance should revert
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun flow16_deleteExpense_balanceReverted() = runBlocking {
        // Create expense
        val expense = Expense(
            account = "Test1",
            amount = BigDecimal("20.00"),
            currency = "EUR",
            category = "Default",
            type = "Expense"
        )
        val expenseId = ledgerDao.addExpenseAndAdjust(expense)
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        
        // Delete the expense
        ledgerDao.deleteExpenseAndAdjust(expenseId.toInt())
        
        // Balance should revert to original
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Additional: Income delete also reverts correctly
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun deleteIncome_balanceReverted() = runBlocking {
        // Create income
        val income = Expense(
            account = "Test1",
            amount = BigDecimal("20.00"),
            currency = "EUR",
            category = "Default",
            type = "Income"
        )
        val incomeId = ledgerDao.addExpenseAndAdjust(income)
        assertEquals(BigDecimal("120.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
        
        // Delete the income
        ledgerDao.deleteExpenseAndAdjust(incomeId.toInt())
        
        // Balance should revert
        assertEquals(BigDecimal("100.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Additional: Case-insensitive account lookup
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun caseInsensitiveAccountLookup() = runBlocking {
        val expense = Expense(
            account = "test1", // lowercase
            amount = BigDecimal("20.00"),
            currency = "EUR",
            category = "Default",
            type = "Expense"
        )
        
        ledgerDao.addExpenseAndAdjust(expense)
        
        // Should still affect Test1
        assertEquals(BigDecimal("80.00"), ledgerDao.getAccountByNameOnce("Test1")!!.balance)
    }
}
