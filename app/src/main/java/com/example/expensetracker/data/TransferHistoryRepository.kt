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

    fun getTransferById(transferId: Int): Flow<TransferHistory?> {
        return transferHistoryDao.getTransferById(transferId)
    }

    suspend fun getTransferByIdOnce(transferId: Int): TransferHistory? {
        return transferHistoryDao.getTransferByIdOnce(transferId)
    }

    suspend fun getCountByAccount(accountName: String): Int {
        return transferHistoryDao.getCountByAccount(accountName)
    }

    suspend fun updateAccountName(oldName: String, newName: String) {
        transferHistoryDao.updateSourceAccountName(oldName, newName)
        transferHistoryDao.updateDestinationAccountName(oldName, newName)
    }
}