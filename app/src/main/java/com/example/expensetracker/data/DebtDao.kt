package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: Debt): Long

    @androidx.room.Update
    suspend fun update(debt: Debt)

    @Query("SELECT * FROM debts WHERE parentExpenseId = :expenseId LIMIT 1")
    fun getDebtForExpense(expenseId: Int): Flow<Debt?>
    
    @Query("SELECT * FROM debts WHERE parentExpenseId = :expenseId LIMIT 1")
    suspend fun getDebtForExpenseSync(expenseId: Int): Debt?

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebtById(id: Int): Debt?

    @Query("SELECT * FROM debts")
    fun getAllDebts(): Flow<List<Debt>>
    
    @Query("DELETE FROM debts WHERE id = :debtId")
    suspend fun deleteDebt(debtId: Int)
}
