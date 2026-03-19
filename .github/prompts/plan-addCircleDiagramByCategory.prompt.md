## Plan: Expense/Income Category Diagram Drill-Down

Add an in-header diagram toggle for Expenses and Incomes that expands the header area to show a circular category breakdown and drill-down keyword breakdown, fully synchronized with existing filters and totals logic. Reuse current filtering and currency-conversion paths in `ExpenseViewModel` to keep percentage math correct under multi-currency and active filters, and keep diagram toggle state independent per tab for the current session.

**Steps**
1. Baseline UI integration points in `HomeScreen` and split the total header into a reusable, stateful header section that supports: left diagram icon toggle, centered total text, right sort button, and optional expanded diagram area. This step blocks subsequent UI wiring.
2. Add per-tab diagram UI state (Expenses vs Incomes), session-scoped and independent: `isVisible`, drill-down level (`category` vs `keyword`), selected category (nullable), and generated color map. Keep state across tab switches (and config changes) via `rememberSaveable`; do not persist to DataStore. *depends on 1*
3. Extend `ExpenseViewModel` with breakdown APIs that reuse current conversion/filter assumptions:
- Expose or reconstruct keyword-name mapping needed for keyword aggregation.
- Add `suspend` aggregation functions for filtered expenses/incomes:
  - category totals in current default currency
  - keyword totals within a selected category in current default currency
- Return a view-friendly result including total sum and entries with percentage-ready amounts, plus a missing-rate state consistent with `TotalState.RateMissing`. *parallel with 4 after API signatures are agreed*
4. Build a new composable for circular diagram rendering (Canvas-based, no third-party chart dependency):
- Draw sectors from normalized percentages.
- Render sector labels as `name + percent` (with truncation/fallback for tiny slices).
- Implement hit-testing by touch angle to detect tapped sector.
- Apply pseudo-random but stable-per-label colors (hash-seeded) to satisfy "randomly color coded" without visual flicker between recompositions. *parallel with 3*
5. Wire header interaction behavior in `HomeScreen`:
- Diagram icon left of Total toggles visibility and active color style mirroring filter icon semantics.
- Visible state expands spacing between tab title row and total area; hidden state restores original spacing.
- Opening diagram:
  - if no category filter: show category-level sectors.
  - if category filter already active: open directly in keyword-level drill-down for that category.
- Tapping category sector sets category filter (`setCategoryFilter`) and drills into keyword view.
- Tapping keyword sector applies existing text query filter (`setTextQueryFilter(keywordName)`) and refreshes diagram.
- Hiding diagram preserves currently applied filters and drill-down state; only visibility changes.
- If user clears category/text filters from chips or dialogs, recompute/rebase diagram level accordingly (e.g., no active category => category-level view). *depends on 1,2,3,4*
6. Add accessibility, strings, and test hooks:
- New content descriptions for diagram toggle and sectors.
- Add stable `TestTags` for diagram icon, diagram container, and sector nodes for both tabs.
- Keep semantics robust for Compose UI testing. *depends on 5*
7. Test updates:
- Add/extend instrumented tests validating:
  - independent icon active states across Expense/Income tabs
  - toggle color changes and visibility expand/collapse behavior
  - category sector tap applies category filter + drill-down
  - keyword sector tap applies text query filter
  - clearing category/text filters redraws diagram correctly
  - diagram hide preserves filters
- Add ViewModel/unit tests for aggregation correctness and percentage math under currency conversion and missing-rate scenarios. *depends on 3,5,6*

**Relevant files**
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt` — integrate left diagram icon, expanded header layout, per-tab UI state, and drill-down/filter interactions.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt` — add/ expose filtered aggregation data for category and keyword breakdown using existing conversion logic (`getAmountInCurrentDefault`, `calculateExpensesTotal` behavior parity).
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt` — style reference for active/inactive icon color behavior parity.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/ui/TestTags.kt` — add stable tags for diagram UI automation.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/res/values/strings.xml` — add new labels/content descriptions for diagram toggle, breakdown labels, and accessibility text.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/androidTest/java/com/example/expensetracker/ui/` — add/extend Home-related UI tests for diagram toggling and drill-down filter behavior.

**Verification**
1. Run targeted unit/instrumented tests for new ViewModel aggregation and Home diagram UI interactions.
2. Manual check on Expenses tab:
- toggle icon activates/deactivates color and diagram visibility
- spacing between tab row and total expands/collapses correctly
- category tap applies category filter chip and switches to keyword diagram
- keyword tap applies Comment/Keyword filter chip and redraws
3. Manual check on Incomes tab with different state from Expenses to confirm independent icon state and drill-down context.
4. Clear category and text filters via chips and dialogs; verify diagram recalculates and returns to appropriate level.
5. Validate behavior when conversion rates are missing (diagram should align with totals missing-rate handling and avoid misleading percentages).

**Decisions**
- Keyword sector tap will use existing `textQuery` filter (Comment/Keyword behavior), not a new strict keyword-only filter.
- If category filter is already active when opening diagram, initial view is keyword-level for that category.
- Diagram icon state is session-only (not persisted to `FilterPreferences`), while filters remain persisted as today.

**Further Considerations**
1. Sector label density for many categories/keywords: use min-angle label threshold and optional legend fallback to prevent overlap.
2. "Random" colors should be deterministic per label for readability and UI stability; true per-render randomness would degrade UX and testability.
3. Keep Transfers/Debts out of scope for this feature (explicitly Expenses/Incomes only).
