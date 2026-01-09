## Plan: Global “+ Create Menu” (Expense/Income/Transfer)

Add a bottom-center “+” button on every screen that opens a 3-item menu (Expense/Income/Transfer) while keeping the mic FAB and voice flows unchanged. Reuse the existing `editExpense/0` prefill route pattern (used by “Account/Category Not Found”) by introducing small ViewModel navigation helpers instead of duplicating route strings. For transfers, add an explicit create-mode path (e.g., `editTransfer/0`) and make `EditTransferScreen` + content support insert-on-save when `transferId == 0`. Update UI tests by adding tags for the new menu, a small instrumentation test to validate that the new menu exists on every destination and navigates correctly and a content tests to validate expense/income/transfer create form/save logic.

### Steps
1. Add global “+” overlay/menu in `MainActivity`, keeping existing mic FAB untouched.
2. Add ViewModel navigation helpers near `navigateToFlow` in `ExpenseViewModel` to build/send routes for “create expense/income” reusing the existing `editExpense/0?...` query scheme.
3. Make create-transfer routing explicit in `NavGraph` by supporting `editTransfer/0` as create-mode.
4. Implement transfer create-mode behavior in `EditTransferScreen`: don’t load DB when `transferId == 0`, hide Delete, and Save inserts (not updates).
5. Add ViewModel API for inserting transfers (parallel to `insertExpense`) in `ExpenseViewModel` so create-transfer uses the same error/snackbar patterns as existing flows.
6. Update tests/tags: extend `TestTags` for the new “+” menu + items, add a small instrumentation test for the menu wiring, and add a create-mode transfer content test in `VoiceFlowContentTest`.