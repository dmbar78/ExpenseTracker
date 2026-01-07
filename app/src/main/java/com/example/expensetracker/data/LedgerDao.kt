package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * DAO for atomic ledger operations. All balance-affecting work (expense/transfer
 * insert/update/delete + account balance adjustments) happen inside @Transaction
 * methods to avoid partial updates and stale-snapshot issues.
 */
@Dao
interface LedgerDao {

    // ─────────────────────────────────────────────────────────────────────────────
    // Non-Flow "once" reads (used inside transactions)
    // ─────────────────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM accounts WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getAccountByNameOnce(name: String): Account?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountByIdOnce(id: Int): Account?

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseByIdOnce(id: Int): Expense?

    @Query("SELECT * FROM transfer_history WHERE id = :id LIMIT 1")
    suspend fun getTransferByIdOnce(id: Int): TransferHistory?

    // ─────────────────────────────────────────────────────────────────────────────
    // Basic insert/update helpers (called within transactions)
    // ─────────────────────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertExpenseRow(expense: Expense): Long

    @Update
    suspend fun updateExpenseRow(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)

    @Insert
    suspend fun insertTransferRow(transfer: TransferHistory): Long

    @Update
    suspend fun updateTransferRow(transfer: TransferHistory)

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteTransferById(id: Int)

    @Update
    suspend fun updateAccountRow(account: Account)

    // ─────────────────────────────────────────────────────────────────────────────
    // Expense transactional methods
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Insert a new expense and adjust the associated account balance atomically.
     * - "Expense" type subtracts from balance
     * - "Income" type adds to balance
     * @return the inserted expense ID
     * @throws IllegalStateException if account not found
     */
    @Transaction
    suspend fun addExpenseAndAdjust(expense: Expense): Long {
        val account = getAccountByNameOnce(expense.account)
            ?: throw IllegalStateException("Account '${expense.account}' not found")

        val delta = if (expense.type == "Expense") {
            expense.amount.negate()
        } else {
            expense.amount
        }

        val newBalance = account.balance.add(delta).setScale(2, RoundingMode.HALF_UP)
        updateAccountRow(account.copy(balance = newBalance))

        return insertExpenseRow(expense)
    }

    /**
     * Update an existing expense and adjust account balances atomically.
     * Handles account changes (old account reverts, new account applies).
     * @throws IllegalStateException if expense or required accounts not found
     */
    @Transaction
    suspend fun updateExpenseAndAdjust(updated: Expense) {
        val original = getExpenseByIdOnce(updated.id)
            ?: throw IllegalStateException("Expense with id=${updated.id} not found")

        // Build per-account delta map (case-insensitive keys)
        val deltas = mutableMapOf<String, BigDecimal>()

        // Revert original: if Expense -> add back; if Income -> subtract
        val revertDelta = if (original.type == "Expense") original.amount else original.amount.negate()
        val oldKey = original.account.lowercase()
        deltas[oldKey] = (deltas[oldKey] ?: BigDecimal.ZERO).add(revertDelta)

        // Apply new: if Expense -> subtract; if Income -> add
        val applyDelta = if (updated.type == "Expense") updated.amount.negate() else updated.amount
        val newKey = updated.account.lowercase()
        deltas[newKey] = (deltas[newKey] ?: BigDecimal.ZERO).add(applyDelta)

        // Apply merged deltas to each affected account
        for ((key, delta) in deltas) {
            if (delta.compareTo(BigDecimal.ZERO) == 0) continue
            val acc = getAccountByNameOnce(key)
                ?: throw IllegalStateException("Account '$key' not found")
            val newBalance = acc.balance.add(delta).setScale(2, RoundingMode.HALF_UP)
            updateAccountRow(acc.copy(balance = newBalance))
        }

        updateExpenseRow(updated)
    }

    /**
     * Delete an expense by ID and revert its balance impact atomically.
     * @throws IllegalStateException if expense or account not found
     */
    @Transaction
    suspend fun deleteExpenseAndAdjust(expenseId: Int) {
        val expense = getExpenseByIdOnce(expenseId)
            ?: throw IllegalStateException("Expense with id=$expenseId not found")

        val account = getAccountByNameOnce(expense.account)
            ?: throw IllegalStateException("Account '${expense.account}' not found")

        // Revert: if Expense -> add back; if Income -> subtract
        val revertDelta = if (expense.type == "Expense") expense.amount else expense.amount.negate()
        val newBalance = account.balance.add(revertDelta).setScale(2, RoundingMode.HALF_UP)
        updateAccountRow(account.copy(balance = newBalance))

        deleteExpenseById(expenseId)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Transfer transactional methods
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Insert a new transfer and adjust source/destination account balances atomically.
     * - Source account: subtract amount
     * - Destination account: add amount
     * @return the inserted transfer ID
     * @throws IllegalStateException if source or destination account not found
     */
    @Transaction
    suspend fun addTransferAndAdjust(transfer: TransferHistory): Long {
        val sourceAcc = getAccountByNameOnce(transfer.sourceAccount)
            ?: throw IllegalStateException("Source account '${transfer.sourceAccount}' not found")
        val destAcc = getAccountByNameOnce(transfer.destinationAccount)
            ?: throw IllegalStateException("Destination account '${transfer.destinationAccount}' not found")

        // Handle same-account edge case (source == dest): no balance change needed
        val sourceKey = transfer.sourceAccount.lowercase()
        val destKey = transfer.destinationAccount.lowercase()

        if (sourceKey == destKey) {
            // Same account - balance unchanged, just record transfer
            return insertTransferRow(transfer)
        }

        val amount = transfer.amount.setScale(2, RoundingMode.HALF_UP)
        val newSourceBalance = sourceAcc.balance.subtract(amount).setScale(2, RoundingMode.HALF_UP)
        val newDestBalance = destAcc.balance.add(amount).setScale(2, RoundingMode.HALF_UP)

        updateAccountRow(sourceAcc.copy(balance = newSourceBalance))
        updateAccountRow(destAcc.copy(balance = newDestBalance))

        return insertTransferRow(transfer)
    }

    /**
     * Update an existing transfer and adjust account balances atomically.
     * Uses merged per-account deltas to handle account changes and overlaps.
     * @throws IllegalStateException if transfer or required accounts not found
     */
    @Transaction
    suspend fun updateTransferAndAdjust(updated: TransferHistory) {
        val original = getTransferByIdOnce(updated.id)
            ?: throw IllegalStateException("Transfer with id=${updated.id} not found")

        // Build per-account delta map (case-insensitive keys)
        val deltas = mutableMapOf<String, BigDecimal>()

        val oldSourceKey = original.sourceAccount.lowercase()
        val oldDestKey = original.destinationAccount.lowercase()
        val newSourceKey = updated.sourceAccount.lowercase()
        val newDestKey = updated.destinationAccount.lowercase()

        // Revert original transfer (unless source==dest)
        if (oldSourceKey != oldDestKey) {
            // Add back to old source
            deltas[oldSourceKey] = (deltas[oldSourceKey] ?: BigDecimal.ZERO).add(original.amount)
            // Subtract from old dest
            deltas[oldDestKey] = (deltas[oldDestKey] ?: BigDecimal.ZERO).subtract(original.amount)
        }

        // Apply new transfer (unless source==dest)
        if (newSourceKey != newDestKey) {
            // Subtract from new source
            deltas[newSourceKey] = (deltas[newSourceKey] ?: BigDecimal.ZERO).subtract(updated.amount)
            // Add to new dest
            deltas[newDestKey] = (deltas[newDestKey] ?: BigDecimal.ZERO).add(updated.amount)
        }

        // Apply merged deltas to each affected account
        for ((key, delta) in deltas) {
            if (delta.compareTo(BigDecimal.ZERO) == 0) continue
            val acc = getAccountByNameOnce(key)
                ?: throw IllegalStateException("Account '$key' not found")
            val newBalance = acc.balance.add(delta).setScale(2, RoundingMode.HALF_UP)
            updateAccountRow(acc.copy(balance = newBalance))
        }

        updateTransferRow(updated)
    }

    /**
     * Delete a transfer by ID and revert its balance impact atomically.
     * @throws IllegalStateException if transfer or required accounts not found
     */
    @Transaction
    suspend fun deleteTransferAndAdjust(transferId: Int) {
        val transfer = getTransferByIdOnce(transferId)
            ?: throw IllegalStateException("Transfer with id=$transferId not found")

        val sourceKey = transfer.sourceAccount.lowercase()
        val destKey = transfer.destinationAccount.lowercase()

        // Revert: add back to source, subtract from dest (unless same account)
        if (sourceKey != destKey) {
            val sourceAcc = getAccountByNameOnce(transfer.sourceAccount)
                ?: throw IllegalStateException("Source account '${transfer.sourceAccount}' not found")
            val destAcc = getAccountByNameOnce(transfer.destinationAccount)
                ?: throw IllegalStateException("Destination account '${transfer.destinationAccount}' not found")

            val amount = transfer.amount.setScale(2, RoundingMode.HALF_UP)
            val newSourceBalance = sourceAcc.balance.add(amount).setScale(2, RoundingMode.HALF_UP)
            val newDestBalance = destAcc.balance.subtract(amount).setScale(2, RoundingMode.HALF_UP)

            updateAccountRow(sourceAcc.copy(balance = newSourceBalance))
            updateAccountRow(destAcc.copy(balance = newDestBalance))
        }

        deleteTransferById(transferId)
    }
}
