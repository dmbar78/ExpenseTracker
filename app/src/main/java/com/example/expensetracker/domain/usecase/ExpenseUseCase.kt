package com.example.expensetracker.domain.usecase

import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.ExpenseRepository
import com.example.expensetracker.data.KeywordDao
import com.example.expensetracker.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Use case for managing expense and income operations.
 * 
 * Encapsulates business logic for:
 * - Creating, updating, and deleting expenses/incomes
 * - Managing keywords associated with expenses
 * - Ensuring atomic balance updates through LedgerRepository
 */
class ExpenseUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val expenseRepository: ExpenseRepository,
    private val keywordDao: KeywordDao
) {
    /**
     * Get all expenses as a Flow.
     */
    fun getExpensesByType(type: String): Flow<List<Expense>> {
        return expenseRepository.getExpensesByType(type)
    }

    /**
     * Get an expense by ID.
     */
    fun getExpenseById(id: Int): Flow<Expense?> {
        return expenseRepository.getExpenseById(id)
    }

    /**
     * Get an expense by ID (one-shot).
     */
    suspend fun getExpenseByIdOnce(id: Int): Expense? {
        return expenseRepository.getExpenseByIdOnce(id)
    }

    /**
     * Insert a new expense with atomic balance update.
     * 
     * @return Result with the inserted expense ID on success
     */
    suspend fun insertExpense(expense: Expense): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val id = ledgerRepository.addExpense(expense)
            Result.success(id)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Insert a new expense with keywords and atomic balance update.
     * 
     * @return Result with the inserted expense ID on success
     */
    suspend fun insertExpenseWithKeywords(expense: Expense, keywordIds: Set<Int>): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val expenseId = ledgerRepository.addExpense(expense)
            if (keywordIds.isNotEmpty()) {
                keywordDao.setKeywordsForExpense(expenseId.toInt(), keywordIds)
            }
            Result.success(expenseId)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing expense with atomic balance update.
     * 
     * @return Result indicating success or failure
     */
    suspend fun updateExpense(expense: Expense): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ledgerRepository.updateExpense(expense)
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing expense with keywords and atomic balance update.
     * 
     * @return Result indicating success or failure
     */
    suspend fun updateExpenseWithKeywords(expense: Expense, keywordIds: Set<Int>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ledgerRepository.updateExpense(expense)
            keywordDao.setKeywordsForExpense(expense.id, keywordIds)
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete an expense with atomic balance reversion.
     * 
     * @return Result indicating success or failure
     */
    suspend fun deleteExpense(expenseId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ledgerRepository.deleteExpense(expenseId)
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the keyword IDs associated with an expense.
     */
    suspend fun getKeywordIdsForExpense(expenseId: Int): Set<Int> {
        return keywordDao.getKeywordIdsForExpense(expenseId).toSet()
    }

    /**
     * Validates an expense before saving.
     * 
     * @return null if valid, error message if invalid
     */
    fun validateExpense(expense: Expense): String? {
        if (expense.account.isBlank()) {
            return "Account is required"
        }
        if (expense.category.isBlank()) {
            return "Category is required"
        }
        if (expense.amount <= BigDecimal.ZERO) {
            return "Amount must be greater than zero"
        }
        if (expense.currency.isBlank()) {
            return "Currency is required"
        }
        return null
    }
}
