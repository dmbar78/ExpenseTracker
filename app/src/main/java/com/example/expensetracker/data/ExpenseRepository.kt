package com.example.expensetracker.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    // Get all expenses as a Flow for live updates
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    fun getExpensesByType(type: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByType(type)
    }

    // Suspend function to insert an expense
    suspend fun insert(expense: Expense): Long {
        return expenseDao.insert(expense)
    }

    suspend fun update(expense: Expense) {
        expenseDao.update(expense)
    }

    fun getExpenseById(expenseId: Int): Flow<Expense?> {
        return expenseDao.getExpenseById(expenseId)
    }

    suspend fun getExpenseByIdOnce(expenseId: Int): Expense? {
        return expenseDao.getExpenseByIdOnce(expenseId)
    }
    
    fun getExpensesByRelatedDebtId(debtId: Int): Flow<List<Expense>> {
        return expenseDao.getExpensesByRelatedDebtId(debtId)
    }

    suspend fun getCountByAccount(accountName: String): Int {
        return expenseDao.getCountByAccount(accountName)
    }

    suspend fun delete(expense: Expense) {
        expenseDao.delete(expense)
    }

    suspend fun updateAccountName(oldName: String, newName: String) {
        expenseDao.updateAccountName(oldName, newName)
    }

    suspend fun updateCategoryName(oldName: String, newName: String) {
        expenseDao.updateCategoryName(oldName, newName)
    }
    suspend fun getPotentialDebtPayments(type: String): List<Expense> {
        return expenseDao.getPotentialDebtPayments(type)
    }
}