## Plan: HomeScreen Dynamic Filter System

Add a Home-only filter control (bottom-left icon) that opens a small popup menu and drives a shared `FilterState` in `ExpenseViewModel`. Apply that filter state to the three existing flows (`allExpenses`, `allIncomes`, `allTransfers`) so filtering is dynamic across tab switches. Represent active filters as removable “planks/chips” above the tabs, and persist the filter state across sessions (recommend DataStore; Room settings table if you prefer DB).

### Steps
1. Define a unified `FilterState` + time modes in `ExpenseViewModel` (e.g., `TimeFilter.Day/Week/Month/Year/Period`, plus account/category/from-to fields) and expose `StateFlow<FilterState>` with setters and `reset*` APIs.
2. Wire dynamic filtering in the ViewModel by deriving `filteredExpenses`, `filteredIncomes`, `filteredTransfers` from current flows + `FilterState` (combine and filter by `Expense.expenseDate` vs `TransferHistory.date`, plus `account`, `category`, `sourceAccount`, `destinationAccount`), then update `HomeScreen` to use the filtered flows in `TransactionList` / `TransfersTab`.
3. Implement the HomeScreen filter icon + popup menus in `HomeScreen`: bottom-left icon always visible; first popup items Time / Expense-Income Account / Transfer From-To / Category / Reset All; “Time” opens the second popup with Day/Week/Month/Year/Period.
4. Implement the picker dialogs for each filter choice using existing patterns from `EditExpenseScreen` and `EditTransferScreen`: Day uses `DatePickerDialog`; Week/Month/Year can select a representative date/year/month and convert to a computed range; Period uses a scrollable range picker (or `DateRangePicker` if already available in your Material3 version) plus “All Time” option (needs earliest transaction date from repos/DAO).
5. Add the “selected filters planks” row above the tabs in `HomeScreen`: show one chip per active filter with value text + trailing “x” to clear that single filter; tapping the chip re-opens the corresponding picker dialog for refinement.
6. Persist and restore filters across sessions: add a small persistence layer via Proto DataStore and load it into `ExpenseViewModel` on init; write-through on each filter change.
7. “OK acts as Cancel if no value chosen”: means to do nothing when selection is null.
8. Week/Month/Year picker UX: needs a dedicated week/month/year picker UI for simplicity.