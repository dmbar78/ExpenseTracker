package com.example.expensetracker.data

import kotlinx.coroutines.flow.Flow

class DebtRepository(private val debtDao: DebtDao) {
    suspend fun insertDebt(debt: Debt): Long {
        return debtDao.insert(debt)
    }

    fun getDebtForExpense(expenseId: Int): Flow<Debt?> {
        return debtDao.getDebtForExpense(expenseId)
    }
    
    suspend fun getDebtForExpenseSync(expenseId: Int): Debt? {
        return debtDao.getDebtForExpenseSync(expenseId)
    }

    suspend fun getDebtById(id: Int): Debt? {
        return debtDao.getDebtById(id)
    }

    fun getAllDebts(): Flow<List<Debt>> {
        return debtDao.getAllDebts()
    }
    
    suspend fun updateDebt(debt: Debt) {
        debtDao.update(debt)
    }

    suspend fun deleteDebt(debtId: Int) {
        debtDao.deleteDebt(debtId)
    }
}
