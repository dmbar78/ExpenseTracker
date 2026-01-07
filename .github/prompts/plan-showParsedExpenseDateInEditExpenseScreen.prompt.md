## Plan: Show Parsed Voice Date in EditExpenseScreen

Pass the parsed `expenseDate` from voice parsing through the `editExpense` navigation route and use it to initialize `EditExpenseScreen`’s `expenseDate` state when `expenseId == 0`. This prevents the screen from defaulting to “today” on the Account/Category Not Found recovery path, while keeping the existing DB-load behavior for `expenseId > 0`.

### Steps
1. Add `expenseDateMillis` query param + `navArgument` in `NavGraph.kt` `NavGraph` `editExpense` composable.
2. Parse `expenseDateMillis` from `backStackEntry.arguments` and pass it into `EditExpenseScreen(...)` as `initialExpenseDateMillis`.
3. Update `EditExpenseScreen.kt` `EditExpenseScreen` signature to accept `initialExpenseDateMillis: Long? = null`.
4. Initialize `expenseDate` state from `initialExpenseDateMillis ?: System.currentTimeMillis()` only when `expenseId == 0`.
5. Set the date picker’s `Calendar` initial time from `expenseDate` so the picker opens on the parsed day.

### Further Considerations
1. Encode `accountName/categoryName/type` in `ExpenseViewModel`’s `_navigateTo.send(...)` to avoid query parsing breaks.
