## Plan: Fix Category Case Mismatch Save Block

When voice parsing reroutes to the “Add Expense” screen due to Account Not Found, the category name can be passed with spoken casing (e.g., “food”) instead of the DB’s canonical casing (“Food”). The screen’s Save validation is currently case-sensitive, so it treats the category as “not selected” until the user re-picks it from the dropdown. Fix by normalizing the category name.

### Steps
1. Identify the “Account Not Found” reroute path in `ExpenseViewModel.kt` around `processParsedExpense` and the navigation event that opens `EditExpenseScreen`.
2. Update `processParsedExpense` to pass the canonical category name from the matched DB record (e.g., `matchedCategory.name`) instead of the raw parsed string when navigating to `EditExpenseScreen`.
3. Repeat the same normalization/validation approach for account name if it’s also validated case-sensitively (prevents the same bug class for accounts).