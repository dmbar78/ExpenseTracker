package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordDao {
    @Insert
    suspend fun insert(keyword: Keyword): Long

    @Query("SELECT * FROM keywords ORDER BY name ASC")
    fun getAllKeywords(): Flow<List<Keyword>>

    @Query("SELECT * FROM keywords WHERE id = :keywordId")
    suspend fun getKeywordById(keywordId: Int): Keyword?

    @Query("SELECT * FROM keywords WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getKeywordByName(name: String): Keyword?

    // Get keywords for a specific expense via the join table
    @Query("""
        SELECT k.* FROM keywords k
        INNER JOIN expense_keyword_cross_ref ref ON k.id = ref.keywordId
        WHERE ref.expenseId = :expenseId
        ORDER BY k.name ASC
    """)
    fun getKeywordsForExpense(expenseId: Int): Flow<List<Keyword>>

    // Get keyword IDs for a specific expense (non-flow, for one-time lookup)
    @Query("""
        SELECT keywordId FROM expense_keyword_cross_ref
        WHERE expenseId = :expenseId
    """)
    suspend fun getKeywordIdsForExpense(expenseId: Int): List<Int>

    // Get all expense-keyword cross references (for building lookup maps)
    @Query("SELECT * FROM expense_keyword_cross_ref")
    fun getAllExpenseKeywordCrossRefs(): Flow<List<ExpenseKeywordCrossRef>>

    // Insert a single link
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpenseKeywordCrossRef(crossRef: ExpenseKeywordCrossRef)

    // Delete all links for an expense
    @Query("DELETE FROM expense_keyword_cross_ref WHERE expenseId = :expenseId")
    suspend fun deleteKeywordLinksForExpense(expenseId: Int)

    // Transaction to set keywords for an expense (replace all)
    @Transaction
    suspend fun setKeywordsForExpense(expenseId: Int, keywordIds: Set<Int>) {
        deleteKeywordLinksForExpense(expenseId)
        keywordIds.forEach { keywordId ->
            insertExpenseKeywordCrossRef(ExpenseKeywordCrossRef(expenseId, keywordId))
        }
    }
}