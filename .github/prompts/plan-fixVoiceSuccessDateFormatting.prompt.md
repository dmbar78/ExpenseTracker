Plan: Format Voice Success Date dd.MM.yyyy
Update the voice “expense added” success message in ExpenseViewModel to display expenseDate as dd.MM.yyyy instead of the raw epoch millis, without changing how dates are stored in Room (still Long). Implement a tiny formatter helper near the message to keep the change minimal and consistent.

Steps 1–4
Locate the message in ExpenseViewModel.processParsedExpense using finalExpense.expenseDate directly.
Add a small helper in ExpenseViewModel, e.g. private fun formatDate(millis: Long): String using SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).
Replace ${finalExpense.expenseDate} in the success string with ${formatDate(finalExpense.expenseDate)}.
Ensure any other voice success messages that include dates (if any) use the same helper for consistency.
Further Considerations 1–2
Use Locale.getDefault() and device timezone so the shown date matches user expectations.
If later you want reuse across UI and ViewModel, extract the helper to a shared util, but keep this change local for now.