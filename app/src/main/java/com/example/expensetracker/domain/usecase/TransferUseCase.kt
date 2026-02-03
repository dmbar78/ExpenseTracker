package com.example.expensetracker.domain.usecase

import com.example.expensetracker.data.LedgerRepository
import com.example.expensetracker.data.TransferHistory
import com.example.expensetracker.data.TransferHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for managing transfer operations.
 * 
 * Encapsulates business logic for:
 * - Creating, updating, and deleting transfers
 * - Ensuring atomic balance updates through LedgerRepository
 */
class TransferUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val transferHistoryRepository: TransferHistoryRepository
) {
    /**
     * Get all transfers as a Flow.
     */
    val allTransfers: Flow<List<TransferHistory>> = transferHistoryRepository.allTransfers

    /**
     * Get a transfer by ID.
     */
    fun getTransferById(id: Int): Flow<TransferHistory?> {
        return transferHistoryRepository.getTransferById(id)
    }

    /**
     * Get a transfer by ID (one-shot).
     */
    suspend fun getTransferByIdOnce(id: Int): TransferHistory? {
        return transferHistoryRepository.getTransferByIdOnce(id)
    }

    /**
     * Insert a new transfer with atomic balance updates.
     * 
     * @return Result with the inserted transfer ID on success
     */
    suspend fun insertTransfer(transfer: TransferHistory): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val id = ledgerRepository.addTransfer(transfer)
            Result.success(id)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing transfer with atomic balance updates.
     * 
     * @return Result indicating success or failure
     */
    suspend fun updateTransfer(transfer: TransferHistory): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ledgerRepository.updateTransfer(transfer)
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a transfer with atomic balance reversion.
     * 
     * @return Result indicating success or failure
     */
    suspend fun deleteTransfer(transferId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ledgerRepository.deleteTransfer(transferId)
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validates a transfer before saving.
     * 
     * @return null if valid, error message if invalid
     */
    fun validateTransfer(transfer: TransferHistory): String? {
        if (transfer.sourceAccount.isBlank()) {
            return "Source account is required"
        }
        if (transfer.destinationAccount.isBlank()) {
            return "Destination account is required"
        }
        if (transfer.sourceAccount == transfer.destinationAccount) {
            return "Source and destination accounts must be different"
        }
        if (transfer.amount <= java.math.BigDecimal.ZERO) {
            return "Amount must be greater than zero"
        }
        return null
    }
}
