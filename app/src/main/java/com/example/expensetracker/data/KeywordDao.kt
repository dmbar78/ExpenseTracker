package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordDao {
    @Insert
    suspend fun insert(keyword: Keyword)

    @Query("SELECT * FROM keywords ORDER BY name ASC")
    fun getAllKeywords(): Flow<List<Keyword>>
}