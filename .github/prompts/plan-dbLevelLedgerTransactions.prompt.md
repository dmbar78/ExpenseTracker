## Plan: DB-Level Transaction Methods for Ledger Operations

Move all balance-affecting work (expense/transfer insert/update/delete + account balance adjustments) into Room `@Transaction` methods so account balances and ledger rows are updated atomically from DB-fresh state, avoiding `Flow` snapshot staleness and partial updates.

### Steps
1. Add a new `LedgerDao` in `app/src/main/java/com/example/expensetracker/data/LedgerDao.kt` with non-Flow “once” reads: `getAccountByNameOnce`, `getExpenseByIdOnce`, `getTransferByIdOnce`, plus `@Update` helpers for rows.
2. Implement transactional methods in `LedgerDao`: `addExpenseAndAdjust`, `updateExpenseAndAdjust`, `deleteExpenseAndAdjust`, `addTransferAndAdjust`, `updateTransferAndAdjust`, `deleteTransferAndAdjust` using merged per-account deltas and `setScale(2, HALF_UP)`.
3. Expose `ledgerDao()` from `AppDatabase` (`app/src/main/java/com/example/expensetracker/data/AppDatabase.kt`) and create `LedgerRepository` (`app/src/main/java/com/example/expensetracker/data/LedgerRepository.kt`) that calls those `@Transaction` methods.
4. Replace balance-changing logic in `ExpenseViewModel` (`app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt`) to call `ledgerRepository.*` for: `insertExpense`, `updateExpense`, `deleteExpense`, `processParsedTransfer`, `updateTransfer`, `deleteTransfer` (stop using `allAccounts.value`/`selected*` as the source of truth).
5. Ensure update/delete APIs take IDs (e.g., `updateTransferAndAdjust(updated)` loads original by `updated.id`, `deleteTransferAndAdjust(id)`), so original rows are fetched inside the transaction.
6. Add/adjust any constraints/guards inside the transaction methods (safe handling for overlapping accounts like `source==dest`, currency mismatch checks, and “missing account” failures).
7. Preserve existing success messages format:
   - Expense: "${expenseType} of ${finalExpense.amount} ${finalExpense.currency} on ${formatDate(finalExpense.expenseDate)} for account ${finalExpense.account} and category ${finalExpense.category} successfully added."
   - Transfer: "Transfer from ${parsedTransfer.sourceAccountName} to ${parsedTransfer.destAccountName} for ${parsedTransfer.amount} ${sourceAccount.currency} on ${formatDate(transferRecord.date)} successfully added."
8. Preserve custom date parsing for transfers and expenses.
9. Preserve parsed custom date propagation to EditExpense screen in case of "Account not found" or "Category not found" errors.

### Further Considerations
1. Prefer “merge deltas by account key” (case-insensitive) to handle account overlaps correctly in update flows.
2. If you want extra safety, add DAO tests around “update without changes” and “account change” scenarios.
