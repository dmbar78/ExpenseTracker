package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE type = :type ORDER BY expenseDate DESC")
    fun getExpensesByType(type: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    fun getExpenseById(expenseId: Int): Flow<Expense?>
    
    @Query("SELECT * FROM expenses WHERE relatedDebtId = :debtId ORDER BY expenseDate DESC")
    fun getExpensesByRelatedDebtId(debtId: Int): Flow<List<Expense>>

    @Query("SELECT COUNT(*) FROM expenses WHERE account = :accountName")
    suspend fun getCountByAccount(accountName: String): Int

    @Delete
    suspend fun delete(expense: Expense)

    // Backup/Restore operations
    @Query("SELECT * FROM expenses ORDER BY id ASC")
    suspend fun getAllExpensesOnce(): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    @Query("UPDATE expenses SET account = :newName WHERE account = :oldName")
    suspend fun updateAccountName(oldName: String, newName: String)

    @Query("UPDATE expenses SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryName(oldName: String, newName: String)

    @Query("SELECT * FROM expenses WHERE type = :type AND relatedDebtId IS NULL ORDER BY expenseDate DESC")
    suspend fun getPotentialDebtPayments(type: String): List<Expense>

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}