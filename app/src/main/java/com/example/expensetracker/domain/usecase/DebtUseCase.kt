package com.example.expensetracker.domain.usecase

import com.example.expensetracker.data.Debt
import com.example.expensetracker.data.DebtRepository
import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.ExpenseRepository
import com.example.expensetracker.data.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Use case for managing debt operations.
 * 
 * Encapsulates business logic for:
 * - Creating and deleting debts
 * - Calculating paid amounts with currency conversion
 * - Checking and updating debt status
 * - Linking/unlinking payments to debts
 */
class DebtUseCase @Inject constructor(
    private val debtRepository: DebtRepository,
    private val expenseRepository: ExpenseRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) {
    /**
     * Creates a new debt record for an expense.
     */
    suspend fun createDebt(parentExpenseId: Int, notes: String? = null): Result<Long> {
        return try {
            val debt = Debt(
                parentExpenseId = parentExpenseId,
                notes = notes,
                status = "OPEN"
            )
            val id = debtRepository.insertDebt(debt)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a debt record.
     */
    suspend fun deleteDebt(debtId: Int): Result<Unit> {
        return try {
            debtRepository.deleteDebt(debtId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets the debt associated with an expense.
     */
    fun getDebtForExpense(expenseId: Int): Flow<Debt?> {
        return debtRepository.getDebtForExpense(expenseId)
    }

    /**
     * Gets the debt associated with an expense (one-shot).
     */
    suspend fun getDebtForExpenseSync(expenseId: Int): Debt? {
        return debtRepository.getDebtForExpenseSync(expenseId)
    }

    /**
     * Gets all payments linked to a debt.
     */
    fun getPaymentsForDebt(debtId: Int): Flow<List<Expense>> {
        return expenseRepository.getExpensesByRelatedDebtId(debtId)
    }

    /**
     * Calculates the total amount paid towards a debt in the debt's original currency.
     * Payments are converted to the debt's currency based on the exchange rate
     * at the time of the payment.
     */
    suspend fun calculateDebtPaidAmount(debtId: Int, debtCurrency: String): BigDecimal {
        val payments = expenseRepository.getExpensesByRelatedDebtId(debtId).first()
        var totalPaid = BigDecimal.ZERO

        for (payment in payments) {
            val amountInDebtCurrency = getAmountInTargetCurrency(
                amount = payment.amount,
                sourceCurrency = payment.currency,
                targetCurrency = debtCurrency,
                date = payment.expenseDate
            )

            if (amountInDebtCurrency != null) {
                totalPaid = totalPaid.add(amountInDebtCurrency)
            }
        }

        return totalPaid
    }

    /**
     * Checks if a debt should be closed based on payments and updates its status.
     * 
     * @return true if the debt status was changed
     */
    suspend fun checkAndUpdateDebtStatus(debtId: Int): Boolean {
        val debt = debtRepository.getDebtById(debtId) ?: return false
        val parentExpense = expenseRepository.getExpenseById(debt.parentExpenseId).first() ?: return false

        val paidAmount = calculateDebtPaidAmount(debtId, parentExpense.currency)

        val newStatus = if (paidAmount >= parentExpense.amount) "CLOSED" else "OPEN"

        if (debt.status != newStatus) {
            debtRepository.updateDebt(debt.copy(status = newStatus))
            return true
        }
        return false
    }

    /**
     * Converts payment amounts to a target currency for display purposes.
     */
    suspend fun getConvertedPaymentAmounts(payments: List<Expense>, targetCurrency: String): Map<Int, BigDecimal> {
        val conversionMap = mutableMapOf<Int, BigDecimal>()
        for (payment in payments) {
            val convertedAmount = getAmountInTargetCurrency(
                amount = payment.amount,
                sourceCurrency = payment.currency,
                targetCurrency = targetCurrency,
                date = payment.expenseDate
            )
            if (convertedAmount != null) {
                conversionMap[payment.id] = convertedAmount
            }
        }
        return conversionMap
    }

    /**
     * Gets potential payments (expenses/incomes) that are not yet linked to any debt.
     */
    suspend fun getPotentialDebtPayments(type: String): List<Expense> {
        return expenseRepository.getPotentialDebtPayments(type)
    }

    /**
     * Helper to convert an amount between currencies at a specific date.
     */
    private suspend fun getAmountInTargetCurrency(
        amount: BigDecimal,
        sourceCurrency: String,
        targetCurrency: String,
        date: Long
    ): BigDecimal? {
        if (sourceCurrency == targetCurrency) return amount

        val rate = exchangeRateRepository.getMostRecentRateOnOrBefore(sourceCurrency, targetCurrency, date)
        return rate?.multiply(amount)
    }
}
