## Plan: Restore “Save Navigates Back” on EditExpense

The EditExpense screen is already set up to close only when `navigateBackFlow` emits, but the expense save methods don’t emit that event. The minimal, consistent fix is to emit the navigate-back event after successful insert/update in the expense ViewModel, mirroring how transfers work.

### Steps
1. Inspect `EditExpenseScreen.kt` for `navigateBackFlow` collection and `navController.popBackStack()` usage.
2. Inspect the expense ViewModel file (search for `class ...ExpenseViewModel` and `navigateBackFlow`) and locate `insertExpense(...)` and `updateExpense(...)`.
3. Compare with transfer ViewModel save methods (`insertTransfer(...)` / `updateTransfer(...)`) to confirm they call `_navigateBackChannel.send(Unit)` (or equivalent) on success.
4. Add the same “emit navigate back” call after successful `ledgerRepository.addExpense(...)` in `insertExpense(...)` and after `ledgerRepository.updateExpense(...)` in `updateExpense(...)`.
5. Re-run the create/edit expense flow and confirm Save now pops back reliably (same behavior as transfer).
6. Analyze if any UI tests need updates to reflect this behavior change (e.g., waiting for navigation after save).
7. Update or add tests as needed to verify that saving an expense navigates back only on success.
8. Check if voice flows to create expense or transfer use same inser/update methods as UI; if so, ensure no unintended navigation occurs in those contexts.
9. Check if voice flow tests in `VoiceFlowContentTest.kt` need adjustments to account for the navigation change.