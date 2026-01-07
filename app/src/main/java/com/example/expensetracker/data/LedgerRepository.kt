package com.example.expensetracker.data

/**
 * Repository that wraps LedgerDao transaction methods for atomic ledger operations.
 * All balance-affecting work goes through this repository to ensure account balances
 * and ledger rows are updated atomically.
 */
class LedgerRepository(private val ledgerDao: LedgerDao) {

    // ─────────────────────────────────────────────────────────────────────────────
    // Expense operations
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Insert a new expense and adjust the associated account balance atomically.
     * @return the inserted expense ID
     * @throws IllegalStateException if account not found
     */
    suspend fun addExpense(expense: Expense): Long {
        return ledgerDao.addExpenseAndAdjust(expense)
    }

    /**
     * Update an existing expense and adjust account balances atomically.
     * Handles account changes (old account reverts, new account applies).
     * @throws IllegalStateException if expense or required accounts not found
     */
    suspend fun updateExpense(expense: Expense) {
        ledgerDao.updateExpenseAndAdjust(expense)
    }

    /**
     * Delete an expense by ID and revert its balance impact atomically.
     * @throws IllegalStateException if expense or account not found
     */
    suspend fun deleteExpense(expenseId: Int) {
        ledgerDao.deleteExpenseAndAdjust(expenseId)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Transfer operations
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Insert a new transfer and adjust source/destination account balances atomically.
     * @return the inserted transfer ID
     * @throws IllegalStateException if source or destination account not found
     */
    suspend fun addTransfer(transfer: TransferHistory): Long {
        return ledgerDao.addTransferAndAdjust(transfer)
    }

    /**
     * Update an existing transfer and adjust account balances atomically.
     * @throws IllegalStateException if transfer or required accounts not found
     */
    suspend fun updateTransfer(transfer: TransferHistory) {
        ledgerDao.updateTransferAndAdjust(transfer)
    }

    /**
     * Delete a transfer by ID and revert its balance impact atomically.
     * @throws IllegalStateException if transfer or required accounts not found
     */
    suspend fun deleteTransfer(transferId: Int) {
        ledgerDao.deleteTransferAndAdjust(transferId)
    }
}
