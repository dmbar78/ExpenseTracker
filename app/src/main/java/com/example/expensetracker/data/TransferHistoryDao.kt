package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {
    @Insert
    suspend fun insert(transferHistory: TransferHistory)

    @Update
    suspend fun update(transferHistory: TransferHistory)

    @Delete
    suspend fun delete(transferHistory: TransferHistory)

    @Query("SELECT * FROM transfer_history ORDER BY date DESC")
    fun getAllTransfers(): Flow<List<TransferHistory>>

    @Query("SELECT * FROM transfer_history WHERE id = :transferId")
    fun getTransferById(transferId: Int): Flow<TransferHistory>
}