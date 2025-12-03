package com.example.expensetracker.data

import kotlinx.coroutines.flow.Flow

class TransferHistoryRepository(private val transferHistoryDao: TransferHistoryDao) {

    val allTransfers: Flow<List<TransferHistory>> = transferHistoryDao.getAllTransfers()

    suspend fun insert(transferHistory: TransferHistory) {
        transferHistoryDao.insert(transferHistory)
    }

    suspend fun update(transferHistory: TransferHistory) {
        transferHistoryDao.update(transferHistory)
    }

    suspend fun delete(transferHistory: TransferHistory) {
        transferHistoryDao.delete(transferHistory)
    }

    fun getTransferById(transferId: Int): Flow<TransferHistory> {
        return transferHistoryDao.getTransferById(transferId)
    }
}