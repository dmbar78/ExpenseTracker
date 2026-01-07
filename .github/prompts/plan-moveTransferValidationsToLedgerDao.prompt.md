## Plan: Move Transfer Validations to LedgerDao

Move the "Same Account" and "Currency Mismatch" validations from the ViewModel into the `LedgerDao` transaction to ensure all transfers satisfy business rules atomically, regardless of entry point.

### Steps
1. Modify `addTransferAndAdjust` and `updateTransferAndAdjust` in [app/src/main/java/com/example/expensetracker/data/LedgerDao.kt](../../app/src/main/java/com/example/expensetracker/data/LedgerDao.kt) to perform checks after fetching accounts, throwing `IllegalArgumentException` with specific messages if validation fails.
2. Update `ExpenseViewModel.kt` in `processParsedTransfer` to remove the explicit `if` validations.
3. Wrap the `ledgerRepository.addTransfer` and `ledgerRepository.updateTransfer` calls in `ExpenseViewModel.kt` in a `try/catch` blocks.
4. Catch the exceptions in the ViewModel and map the error messages to `VoiceRecognitionState.SameAccountTransfer` or `VoiceRecognitionState.TransferCurrencyMismatch`.
5. Collect and show ViewModel errors in `EditTransferScreen.kt`.

### Further Considerations
1. To make error handling less brittle than string matching, consider adding specific exception classes (e.g., `SameAccountException`, `CurrencyMismatchException`) in a suitable location (like `LedgerDao.kt` or a new file).
2. The DAO is currently treating `sourceKey == destKey` as a valid "no-op" for balances; this logic will be replaced by the validation exception.