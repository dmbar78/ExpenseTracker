## Plan: Keywords Multi-Select on Edit Expense

Add keyword tagging for expenses using the existing Room `Keyword` entity, a new many-to-many join table, and a searchable multi-select dropdown in the Edit Expense UI. The UI keeps keyword selections across open/close, supports “contains” filtering via free text input, pins selected keywords to the top, supports creating a new keyword from the dropdown without losing checkbox state, and shows selected keywords as removable planks/chips.

### Steps
1. Add many-to-many storage for Expense ↔ Keyword relationships.
   - Create a join entity like `ExpenseKeywordCrossRef(expenseId, keywordId)` with a unique index on `(expenseId, keywordId)` and foreign keys (cascade delete).
   - Update `app/src/main/java/com/example/expensetracker/data/AppDatabase.kt` entities list, bump DB version, and add a migration to create the join table (and indexes).

2. Make `Keyword` behave like Category (stable + deduped).
   - Update `app/src/main/java/com/example/expensetracker/data/Keyword.kt` to add a unique index on `name` and NOCASE collation (mirrors Category behavior) so “food” and “Food” don’t duplicate.
   - Update the migration to create the unique index / collation change (or do a create-copy-rename if needed for collation).

3. Add DAO/repository APIs for keywords + expense keyword links.
   - Extend `app/src/main/java/com/example/expensetracker/data/KeywordDao.kt`:
     - Make `insert(...)` return the inserted ID (`Long`) so UI can auto-select the new keyword.
     - Add query to get keywords for a given expense (`Flow<List<Keyword>>`) via the join table.
     - Add a “set keywords for expense” transaction: delete existing links for expense, then insert the new set.
   - Add a small `KeywordRepository` (optional) to wrap these operations.

4. Wire ViewModel support (load + save with keywords).
   - In `app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt`:
     - Expose `allKeywords: Flow<List<Keyword>>` from `keywordDao.getAllKeywords()`.
     - Expose `keywordsForExpense(expenseId): Flow<List<Keyword>>` for edit mode initialization.
     - Update save paths so keywords persist:
       - On insert: call `ledgerRepository.addExpense(expense)` to get `expenseId`, then set keyword links for that new ID.
       - On update: update expense, then replace keyword links for that existing `expenseId`.
     - Keep the navigation behavior unchanged (still use your existing success flow), but ensure keyword linking happens before emitting navigate-back.

5. Implement the dropdown UI in EditExpense content, placed after Currency and before Comment.
   - In `app/src/main/java/com/example/expensetracker/ui/screens/content/EditExpenseScreenContent.kt`:
     - Add a new “Keywords” field using `ExposedDropdownMenuBox` + editable `OutlinedTextField` for free text filtering.
     - Maintain local UI state:
       - `keywordQuery` (free text input; “contains” match, ignore case)
       - `selectedKeywordIds` (persist across open/close in the parent screen via state passed in)
     - Build the visible list as:
       - filter: keyword name contains `keywordQuery` (leading/trailing/middle)
       - sort: selected first, then name ascending
     - Render each keyword as a `DropdownMenuItem` with a `Checkbox`; clicking toggles selection but does not close the menu.
     - Add “Create New…” as the first menu item; show a divider only when there is at least one existing keyword record available.
     - “Create New…” opens an `AlertDialog` with a text field prefilled from `keywordQuery`; OK inserts the keyword and returns to the dropdown with selection preserved (auto-check the newly created keyword after OK).
     - Selected keywords should be shown in the Keywords field as planks/chips, visually similar to already implemented filter planks:
       - Each selected keyword is rendered as a plank/chip with a cross (X) icon.
       - Clicking the X immediately removes (unselects) the keyword.
       - Clicking on the plank body does nothing.
       - Removing via X must preserve other selections and the current filter text.

### Further Considerations
1. Import/export: if you later add CSV export, include keyword names (or IDs) per expense; the join table makes this straightforward.
