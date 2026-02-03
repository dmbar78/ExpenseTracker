package com.example.expensetracker.domain.usecase

import com.example.expensetracker.data.Debt
import com.example.expensetracker.data.DebtRepository
import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.ExpenseRepository
import com.example.expensetracker.data.ExchangeRateRepository
import com.example.expensetracker.data.RateResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Unit tests for DebtUseCase.
 * Tests debt creation, status calculation, and payment tracking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner.Silent::class)
class DebtUseCaseTest {

    @Mock
    private lateinit var debtRepository: DebtRepository

    @Mock
    private lateinit var expenseRepository: ExpenseRepository

    @Mock
    private lateinit var exchangeRateRepository: ExchangeRateRepository

    private lateinit var debtUseCase: DebtUseCase

    private val today = System.currentTimeMillis()

    @Before
    fun setup() {
        debtUseCase = DebtUseCase(debtRepository, expenseRepository, exchangeRateRepository)
    }

    // ========== Create Debt Tests ==========

    @Test
    fun `createDebt succeeds with valid parent expense`() = runTest {
        whenever(debtRepository.insertDebt(any())).thenReturn(1L)

        val result = debtUseCase.createDebt(parentExpenseId = 1, notes = "Test note")

        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrNull())
    }

    @Test
    fun `createDebt fails when repository throws exception`() = runTest {
        whenever(debtRepository.insertDebt(any())).thenThrow(RuntimeException("Database error"))

        val result = debtUseCase.createDebt(parentExpenseId = 1)

        assertTrue(result.isFailure)
    }

    // ========== Delete Debt Tests ==========

    @Test
    fun `deleteDebt succeeds`() = runTest {
        val result = debtUseCase.deleteDebt(1)

        assertTrue(result.isSuccess)
        verify(debtRepository).deleteDebt(1)
    }

    @Test
    fun `deleteDebt fails when repository throws exception`() = runTest {
        whenever(debtRepository.deleteDebt(any())).thenThrow(RuntimeException("Database error"))

        val result = debtUseCase.deleteDebt(1)

        assertTrue(result.isFailure)
    }

    // ========== Calculate Paid Amount Tests ==========

    @Test
    fun `calculateDebtPaidAmount returns zero when no payments`() = runTest {
        whenever(expenseRepository.getExpensesByRelatedDebtId(1)).thenReturn(flowOf(emptyList()))

        val result = debtUseCase.calculateDebtPaidAmount(1, "USD")

        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun `calculateDebtPaidAmount sums payments in same currency`() = runTest {
        val payments = listOf(
            Expense(
                id = 1, account = "Bank", amount = BigDecimal("50.00"),
                currency = "USD", category = "Payment", expenseDate = today,
                type = "Income", relatedDebtId = 1
            ),
            Expense(
                id = 2, account = "Bank", amount = BigDecimal("30.00"),
                currency = "USD", category = "Payment", expenseDate = today,
                type = "Income", relatedDebtId = 1
            )
        )
        whenever(expenseRepository.getExpensesByRelatedDebtId(1)).thenReturn(flowOf(payments))

        val result = debtUseCase.calculateDebtPaidAmount(1, "USD")

        assertEquals(BigDecimal("80.00"), result)
    }

    @Test
    fun `calculateDebtPaidAmount converts payments from different currency`() = runTest {
        val payments = listOf(
            Expense(
                id = 1, account = "Bank", amount = BigDecimal("100.00"),
                currency = "EUR", category = "Payment", expenseDate = today,
                type = "Income", relatedDebtId = 1
            )
        )
        whenever(expenseRepository.getExpensesByRelatedDebtId(1)).thenReturn(flowOf(payments))
        // EUR to USD rate of 1.1
        whenever(exchangeRateRepository.getMostRecentRateOnOrBefore(eq("EUR"), eq("USD"), any()))
            .thenReturn(BigDecimal("1.1"))

        val result = debtUseCase.calculateDebtPaidAmount(1, "USD")

        // Use compareTo for BigDecimal comparison to avoid scale issues
        assertEquals(0, BigDecimal("110.00").compareTo(result))
    }

    @Test
    fun `calculateDebtPaidAmount skips payments with missing exchange rate`() = runTest {
        val payments = listOf(
            Expense(
                id = 1, account = "Bank", amount = BigDecimal("100.00"),
                currency = "XYZ", // Unknown currency
                category = "Payment", expenseDate = today,
                type = "Income", relatedDebtId = 1
            )
        )
        whenever(expenseRepository.getExpensesByRelatedDebtId(1)).thenReturn(flowOf(payments))
        whenever(exchangeRateRepository.getMostRecentRateOnOrBefore(eq("XYZ"), eq("USD"), any()))
            .thenReturn(null)

        val result = debtUseCase.calculateDebtPaidAmount(1, "USD")

        assertEquals(BigDecimal.ZERO, result)
    }

    // ========== Check and Update Debt Status Tests ==========

    @Test
    fun `checkAndUpdateDebtStatus closes debt when fully paid`() = runTest {
        val debt = Debt(id = 1, parentExpenseId = 10, status = "OPEN")
        val parentExpense = Expense(
            id = 10, account = "Bank", amount = BigDecimal("100.00"),
            currency = "USD", category = "Loan", expenseDate = today,
            type = "Expense"
        )
        val payments = listOf(
            Expense(
                id = 20, account = "Bank", amount = BigDecimal("100.00"),
                currency = "USD", category = "Payment", expenseDate = today,
                type = "Income", relatedDebtId = 1
            )
        )

        whenever(debtRepository.getDebtById(1)).thenReturn(debt)
        whenever(expenseRepository.getExpenseById(10)).thenReturn(flowOf(parentExpense))
        whenever(expenseRepository.getExpensesByRelatedDebtId(1)).thenReturn(flowOf(payments))

        val statusChanged = debtUseCase.checkAndUpdateDebtStatus(1)

        assertTrue(statusChanged)
        verify(debtRepository).updateDebt(debt.copy(status = "CLOSED"))
    }

    @Test
    fun `checkAndUpdateDebtStatus reopens debt when underpaid`() = runTest {
        val debt = Debt(id = 1, parentExpenseId = 10, status = "CLOSED")
        val parentExpense = Expense(
            id = 10, account = "Bank", amount = BigDecimal("100.00"),
            currency = "USD", category = "Loan", expenseDate = today,
            type = "Expense"
        )
        val payments = listOf(
            Expense(
                id = 20, account = "Bank", amount = BigDecimal("50.00"),
                currency = "USD", category = "Payment", expenseDate = today,
                type = "Income", relatedDebtId = 1
            )
        )

        whenever(debtRepository.getDebtById(1)).thenReturn(debt)
        whenever(expenseRepository.getExpenseById(10)).thenReturn(flowOf(parentExpense))
        whenever(expenseRepository.getExpensesByRelatedDebtId(1)).thenReturn(flowOf(payments))

        val statusChanged = debtUseCase.checkAndUpdateDebtStatus(1)

        assertTrue(statusChanged)
        verify(debtRepository).updateDebt(debt.copy(status = "OPEN"))
    }

    @Test
    fun `checkAndUpdateDebtStatus returns false when status unchanged`() = runTest {
        val debt = Debt(id = 1, parentExpenseId = 10, status = "OPEN")
        val parentExpense = Expense(
            id = 10, account = "Bank", amount = BigDecimal("100.00"),
            currency = "USD", category = "Loan", expenseDate = today,
            type = "Expense"
        )
        val payments = listOf(
            Expense(
                id = 20, account = "Bank", amount = BigDecimal("50.00"),
                currency = "USD", category = "Payment", expenseDate = today,
                type = "Income", relatedDebtId = 1
            )
        )

        whenever(debtRepository.getDebtById(1)).thenReturn(debt)
        whenever(expenseRepository.getExpenseById(10)).thenReturn(flowOf(parentExpense))
        whenever(expenseRepository.getExpensesByRelatedDebtId(1)).thenReturn(flowOf(payments))

        val statusChanged = debtUseCase.checkAndUpdateDebtStatus(1)

        assertFalse(statusChanged)
    }

    @Test
    fun `checkAndUpdateDebtStatus returns false when debt not found`() = runTest {
        whenever(debtRepository.getDebtById(1)).thenReturn(null)

        val statusChanged = debtUseCase.checkAndUpdateDebtStatus(1)

        assertFalse(statusChanged)
    }

    @Test
    fun `checkAndUpdateDebtStatus returns false when parent expense not found`() = runTest {
        val debt = Debt(id = 1, parentExpenseId = 10, status = "OPEN")
        whenever(debtRepository.getDebtById(1)).thenReturn(debt)
        whenever(expenseRepository.getExpenseById(10)).thenReturn(flowOf(null))

        val statusChanged = debtUseCase.checkAndUpdateDebtStatus(1)

        assertFalse(statusChanged)
    }

    // ========== Converted Payment Amounts Tests ==========

    @Test
    fun `getConvertedPaymentAmounts returns correct conversions`() = runTest {
        val payments = listOf(
            Expense(
                id = 1, account = "Bank", amount = BigDecimal("100.00"),
                currency = "EUR", category = "Payment", expenseDate = today,
                type = "Income"
            ),
            Expense(
                id = 2, account = "Bank", amount = BigDecimal("50.00"),
                currency = "USD", category = "Payment", expenseDate = today,
                type = "Income"
            )
        )

        whenever(exchangeRateRepository.getMostRecentRateOnOrBefore(eq("EUR"), eq("USD"), any()))
            .thenReturn(BigDecimal("1.1"))

        val result = debtUseCase.getConvertedPaymentAmounts(payments, "USD")

        assertEquals(2, result.size)
        // Use compareTo for BigDecimal comparison to avoid scale issues
        assertEquals(0, BigDecimal("110.00").compareTo(result[1]))
        assertEquals(0, BigDecimal("50.00").compareTo(result[2]))
    }

    @Test
    fun `getConvertedPaymentAmounts skips payments with missing rate`() = runTest {
        val payments = listOf(
            Expense(
                id = 1, account = "Bank", amount = BigDecimal("100.00"),
                currency = "XYZ", category = "Payment", expenseDate = today,
                type = "Income"
            )
        )

        whenever(exchangeRateRepository.getMostRecentRateOnOrBefore(eq("XYZ"), eq("USD"), any()))
            .thenReturn(null)

        val result = debtUseCase.getConvertedPaymentAmounts(payments, "USD")

        assertTrue(result.isEmpty())
    }
}
