package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query("SELECT COUNT(*) FROM transfer_history WHERE sourceAccount = :accountName OR destinationAccount = :accountName")
    suspend fun getCountByAccount(accountName: String): Int

    // Backup/Restore operations
    @Query("SELECT * FROM transfer_history ORDER BY id ASC")
    suspend fun getAllTransfersOnce(): List<TransferHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transfers: List<TransferHistory>)

    @Query("DELETE FROM transfer_history")
    suspend fun deleteAll()
}