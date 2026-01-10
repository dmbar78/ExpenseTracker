## Plan: Success Snackbar Before Auto-Close (Expense + Transfer)

Show a short “Saved” snackbar when the user taps Save, then close the screen only after the snackbar is displayed. Do this in the *screen wrappers* (not voice state), so the behavior applies to normal UI create/edit flows and doesn’t affect voice dialogs.

### Steps
1. Update the navigate-back collector in `EditTransferScreen.kt` to: on `viewModel.navigateBackFlow` emit → call `snackbarHostState.showSnackbar(successMessage)` → then `navController.popBackStack()`.
2. Pick a success message in `EditTransferScreen` based on `isEditMode` (e.g., “Transfer updated” vs “Transfer created”) so it doesn’t require ViewModel changes.
3. Add snackbar support to `EditExpenseScreen.kt`: create `SnackbarHostState`, wrap the content in `Scaffold(snackbarHost=…)`, and apply `Modifier.padding(paddingValues)` to the content.
4. In `EditExpenseScreen`, collect `viewModel.errorFlow` and display those messages via `snackbarHostState.showSnackbar(...)` (mirrors transfer behavior).
5. In `EditExpenseScreen`, change the existing `navigateBackFlow` collector to show a success snackbar first, then pop. Derive the message from `expenseId > 0` and the current `type` (“Expense/Income created/updated”).
6. Ensure `onSave` still only calls `viewModel.insertExpense/updateExpense` and does not pop directly; closing remains driven by the navigate-back collector.
7. Add/adjust one wiring test per screen (instrumented, real `MainActivity`): tap Save → assert snackbar text appears → assert screen pops afterward. Keep content tests unchanged (they don’t cover navigation).

### Further Considerations
1. `SnackbarHostState.showSnackbar(...)` suspends until it dismisses; this plan will keep the screen visible briefly. If you want instant close + snackbar shown on the previous screen, move snackbar hosting to a global `Scaffold` in `MainActivity`/Nav host and emit a “success event” upward instead.
