## Plan: Add Comment/Keyword Text Filter

Add a new HomeScreen filter option “Comment/Keyword” that opens a dialog with a free-text field. When OK is pressed with non-empty text, store it in `FilterState` and apply it to both expenses and transfers by matching `comment` and (for expenses) related keyword names. If OK is pressed with empty text, treat it as Cancel (no state change). Cancel always dismisses the dialog and returns to HomeScreen.

### Steps
1. Extend `FilterState` with an optional `textQuery` field and update `hasActiveFilters()` to include it.
2. Persist/restore `textQuery` alongside other filters (DataStore/Room settings), and add `setTextQuery()` + `clearTextQuery()` APIs in `ExpenseViewModel`.
3. Add a new “Comment/Keyword” item in the HomeScreen filter popup; open a small dialog with a single-line `OutlinedTextField`, plus Cancel/OK actions (OK no-op if blank).
4. scope is “keywords apply to expenses only”.
5. Keyword matching needs keyword names per expense at filter time: prefer in-memory enrichment (expenseId → keyword names flow).
6. Apply filtering in the centralized ViewModel filter pipeline: for expenses match `expense.comment` OR any related keyword name contains `textQuery` (case-insensitive); for transfers match `transfer.comment` contains `textQuery`.
7. Render an active “Comment/Keyword” plank/chip in the filter planks row; include an “x” to clear only `textQuery`, and tapping the plank re-opens the dialog with the current value prefilled.
