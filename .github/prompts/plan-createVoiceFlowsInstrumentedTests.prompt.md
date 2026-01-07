## Plan: Instrumented + Unit Tests For Voice/Ledger Flows

You can cover most of these flows reliably with instrumented Room tests (balances + transfer validations), and cover parsing/date logic with JVM JUnit tests once parsing is extracted from `ExpenseViewModel`. Full end-to-end “simulate spokenText and verify screens” is feasible as Compose UI tests only if you add a small test seam (injectable `ExpenseViewModel` and stable `testTag`s); otherwise it’s brittle because real `SpeechRecognizer` can’t be deterministically driven.

### Flows
0) Create account Test0 with currency = RUB and balance 100
1) Create accounts Test1, Test2, Test3 with currency = EUR and balance 100
2) Create Expense of type Expense from account Test1, amount = 20 EUR, category = default, no spoken date via simulated spokenText variable "Expense from test1 20 category default". Result: expense successfully created, Test1 balance amount = 80 EUR, dated with current date.
3) Create Expense of type Expense from account Test2, amount = 20 EUR, category = test, date = January 1st via simulated spokenText variable "expense from test2 20 category test January First". Result: EditExpenseScreen opened, date field in calendar picker is set to January 1st 2026, account = Test2, amount = 20, currency = EUR, category = unknown (marked as category not found). Open category menu, choose "Create New...". AddCategoryScreen should open with category name prefilled as "test". Press Save should save new category "test", close AddCategoryScreen and navigate back to EditExpenseScreen, where the category field should not be marked with error red border any more. Press Save should close the EditExpenseScreen and expense is successfully created: Test2 balance amount = 80 EUR, dated January 1st 2026.
4) Create Expense of type Income to account Test1, amount = 20 EUR, category = default, no spoken date via simulated spokenText variable "Income to Test1 20 category default". Result: expense successfully created, Test1 balance amount = 100 EUR, dated with current date.
5) Create Expense of type Income to account Test2, amount = 20 EUR, category = income, date = 1st of January  via simulated spokenText variable "Income to Test2 20 category income First of January". Result: EditExpenseScreen opened, date field in calendar picker is set to January 2nd 2026, account = Test2, amount = 20, currency = EUR, category = income (marked as category not found). Open category menu, choose "Create New...". AddCategoryScreen should open with category name prefilled as "income". Press Save should save new category "income", close AddCategoryScreen and navigate back to EditExpenseScreen, where the category field should not be marked with error red border any more. Press Save should close the EditExpenseScreen and expense is successfully created: Test2 balance amount = 100 EUR, dated January 2nd 2026.
6) Create transfer from Test0 to Test1 for 20 RUB with no spoken date via simulated variable spokenText = "transfer from test0 to test1 20". Should bring currency mismatch error.
7) Create transfer from Test0 to Test0 for 20 RUB with no spoken date via simulated variable spokenText = "transfer from test0 to test0 20". Should bring same accounts error.
8) Create transfer from Test1 to Test2 for 20 EUR with no spoken date via simulated variable spokenText = "transfer from test1 to test2 20". Should create transferHistory record, Test1 balance amount = 80, Test2 balance amount = 120 
9) Create transfer from Test2 to Test1 for 20 EUR with date = January 1 via simulated variable spokenText = "transfer from test2 to test1 20 January One". Should create transferHistory record, Test1 balance amount = 100, Test2 balance amount = 100, transferDate = January 1, 2026.
10) Delete transfer record via EditTransferScreen. source and destination accounts balance amounts should be adjusted correctly.
11)  Update transfer record via EditTransferScreen: change amount. source (Test1) and destination (Test2) accounts balance amounts should be adjusted correctly.
12)  Update transfer record via EditTransferScreen: switch accounts, source (Test2) and destination (Test1) accounts balance amounts should be adjusted correctly.
13)  Update transfer record via EditTransferScreen: change destination account. source (Test1) account balance should remain the same, old destination account (Test2) and new destination account (Test3) balance amounts should be adjusted correctly.
14) Update expense record via EditExpenseScreen: change amount. The related account balance amount should be adjusted correctly.
15) Update expense record via EditExpenseScreen: change account. The old and new account balance amounts should be adjusted correctly.
16) Delete expense record via EditExpenseScreen. The related account balance amount should be adjusted correctly.

### Steps
1. Add Room in-memory instrumented test harness under `app/src/androidTest/java` to create `AppDatabase` via `Room.inMemoryDatabaseBuilder` and exercise `LedgerDao`/`LedgerRepository` (balances, inserts, updates, deletes, validations).
2. Add DAO-level instrumented tests for transfers to cover flows 6–13 (currency mismatch, same-account, add/update/delete transfer adjusts both accounts) using `LedgerDao.addTransferAndAdjust`, `updateTransferAndAdjust`, `deleteTransferAndAdjust`.
3. Add DAO-level instrumented tests for expenses to cover flows 2, 4, 14–16 (add/update/delete expense adjusts balances, changing account moves balance correctly) using `LedgerDao.addExpenseAndAdjust`, `updateExpenseAndAdjust`, `deleteExpenseAndAdjust`.
4. Extract parsing + spoken-date parsing into a pure Kotlin helper (new file, e.g., `VoiceCommandParser`) so JVM JUnit tests under `app/src/test/java` can deterministically test flows 2–5, 8–9 parsing pieces (`parseExpense`, `parseTransfer`, `parseTrailingSpokenDate`, money parsing).
5. (Only if you truly need UI navigation assertions like “EditExpenseScreen opened”, “AddCategoryScreen prefilled”, “red border cleared”): add minimal test seams:
   - Allow injecting a test `ExpenseViewModel` into the Activity/NavGraph.
   - Add `Modifier.testTag(...)` for Account/Category/Amount fields, Save buttons, and error labels in `EditExpenseScreen.kt` and AddCategory screen.
   - Write Compose UI tests under `app/src/androidTest/java` using `createAndroidComposeRule`.
6. Allow `Clock/nowProvider` injection to assert “Jan 1, 2026” exactly instead of using the device’s current year/time.
