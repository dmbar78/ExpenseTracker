## Plan: CSV Export With Required Period Dialog

Implement CSV reporting export only (JSON work excluded). Add Export for Reporting (CSV) in Settings > Data Management after Restore from Backup. On tap, require a reporting-period dialog (Day/Week/Month/Year/Period) with prefill from current filter, OK/Cancel behavior, and validation error Please choose reporting period. After valid selection, export period-filtered data into expenses.csv, incomes.csv, transfers.csv, zip them, and save through the existing CreateDocument flow.

**Steps**
1. Phase 1: Entry point and scope guard.
2. Add Export for Reporting (CSV) action in Settings Data Management directly after Restore from Backup.
3. Keep AppDrawer unchanged (navigation to Settings only), with no drawer-level export action.
4. Confirm JSON export/backup paths are untouched and out of scope.

5. Phase 2: Reporting period dialog (blocks export pipeline).
6. On CSV action tap, open a reporting period chooser dialog before starting any file flow.
7. Expose options Day, Week, Month, Year, Period (custom range) by reusing existing picker components.
8. Prefill from active app filter where possible; if filter is unavailable/non-specific, start unselected.
9. Implement actions:
10. OK with valid selection: close dialog and continue to file destination picker.
11. OK with no selection: keep dialog open and show Please choose reporting period.
12. Cancel: close dialog, do not start export.

13. Phase 3: Export orchestration (*depends on Phase 2*).
14. Add dedicated CSV export orchestrator (CsvExportRepository/CsvExportService) separate from JSON backup internals.
15. Add ViewModel trigger that accepts selected period and destination URI.
16. Restrict all exported rows to selected reporting period.
17. Generate exactly three CSV payloads: expenses.csv, incomes.csv, transfers.csv.
18. Zip those payloads and write to selected URI via ContentResolver output stream.

19. Phase 4: CSV schema mapping (*parallel with Phase 5 once period filter contract is fixed*).
20. Expenses/Incomes mapping:
21. Split Expense records by type to expenses.csv and incomes.csv.
22. Include columns: Date, Type, Category, Account, Comment/Note, AmountOriginal, CurrencyOriginal, AmountDefaultAtExport, CurrencyDefaultAtExport.
23. Add Debt (Yes/No), Paid Amount (sum of linked payments for debt rows), Keywords ([k1, k2, ...]).
24. Convert default-at-export amounts using current default currency at export time.
25. Transfers mapping:
26. Include source-side original and default-at-export amounts/currencies.
27. Same-currency rule: target amount/currency equals source amount/currency.
28. Cross-currency rule: target amount uses stored target amount and destination account currency.
29. Include analysis fields (date, source account, destination account, amounts/currencies, comment).

30. Phase 5: Data reuse and performance (*parallel with Phase 4*).
31. Reuse existing conversion/rate logic (no duplicated converter implementation).
32. Reuse existing debt-payment and keyword relation retrieval paths.
33. Build lookup maps once per export batch to avoid N+1 query behavior.

34. Phase 6: Tests and verification.
35. UI tests for period dialog requirements:
36. Export for Reporting (CSV) opens dialog.
37. Dialog displays Day/Week/Month/Year/Period options.
38. Prefill reflects active filter when available.
39. OK without selection shows Please choose reporting period and does not proceed.
40. OK with valid selection proceeds to destination picker/export flow.
41. Cancel closes dialog and leaves Settings unchanged.
42. Unit/integration tests for export correctness:
43. Selected period filtering is applied to all three CSV datasets.
44. Debt/Paid Amount/Keywords fields map correctly.
45. Transfer target rules are correct for same-currency and cross-currency paths.
46. ZIP contains exactly expenses.csv, incomes.csv, transfers.csv with non-empty header rows.

**Relevant files**
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/ui/screens/SettingsScreen.kt — CSV action placement, period dialog flow, validation message, OK/Cancel behavior.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt — reused Day/Week/Month/Year/Period picker components.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt — reference prefill/time-picker interaction patterns.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt — period-aware export trigger/state.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/data/ExpenseDao.kt — period-filtered source rows for expenses/incomes.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/data/TransferHistoryDao.kt — period-filtered source rows for transfers.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/data/DebtDao.kt — debt and payment relations for Paid Amount.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/data/KeywordDao.kt — keyword relation map for Keywords column.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/res/values/strings.xml — Export for Reporting (CSV), Please choose reporting period, success/error texts.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/res/values-ru/strings.xml — RU localization for new CSV strings.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/androidTest/java/com/example/expensetracker/** — Settings dialog and flow UI tests.
- c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/test/java/com/example/expensetracker/** — mapper/filter/ZIP unit and integration tests.

**Verification**
1. Run UI tests validating dialog open/options/prefill/OK validation/Cancel behavior.
2. Run unit tests validating period filtering and row mapping for expenses/incomes/transfers.
3. Run ZIP test asserting exactly three entries with expected names and headers.
4. Manual check: Settings order has Export for Reporting (CSV) immediately after Restore from Backup.
5. Manual check: OK without period shows Please choose reporting period and blocks export.
6. Manual check: valid period selection produces one ZIP with expenses.csv, incomes.csv, transfers.csv.

**Decisions**
- In scope: CSV reporting export only.
- Out of scope: JSON backup/export changes and restore rework.
- Reporting period selection is mandatory before export.
- Dialog must reuse existing period-picker UX patterns for consistency.

**Further Considerations**
1. Non-selected prefill for All Time/None filter vs auto-default to Period. Recommendation: leave unselected and require explicit user choice.
2. Paid Amount for non-debt rows as empty vs 0. Recommendation: 0 for spreadsheet usability.
3. Missing conversion-rate rows during export: hard-fail vs partial blanks. Recommendation: partial blanks with summary warning.