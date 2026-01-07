## Plan: Show Parsed Account/Category On Not Found

When voice parsing can’t find an account/category, the app currently replaces the missing field’s text with “Account Not Found” / “Category Not Found” before navigating. Since EditExpenseScreen already has boolean flags (`initialAccountError` / `initialCategoryError`) to drive the red error UI, we can keep those flags but pass the actually parsed strings as the field text so the user sees what the recognizer heard.

### Steps
1. Update `processParsedExpense` in `ExpenseViewModel.kt` to stop overwriting missing fields with placeholders.
2. When navigating to the `editExpense/0` route, always pass the parsed strings (`parsedExpense.accountName`, `parsedExpense.categoryName`) for any missing field; for found fields, pass canonical DB casing (`account.name`, `category.name`) as you do today.
3. Keep using `accountError` / `categoryError` query params so `EditExpenseScreen.kt` still shows the red border + “not found” message and blocks Save.
4. Continue to show "Account Not Found" / "Category Not Found" in the error Text only in the red border surrounding the field, not in the field value.