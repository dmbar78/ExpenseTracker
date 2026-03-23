## Plan: Home Time-Axis Comparison Bars

Add a Home-level vertical bar comparison diagram (Income, Expense, Profit/Loss) controlled by a new top-app-bar action icon on the Home route. Reuse existing time filter dialogs and existing persistent FilterState behavior; keep chart visibility/navigation UI state session-scoped only. When enabled and time filter is None or AllTime, auto-apply current month. Render 5 periods on the axis (selected period + 4 neighboring periods, capped at current period), support horizontal sliding that updates active time filter and refreshes lists, and handle bar taps for value reveal + tab navigation rules.

**Steps**
1. Phase 1: Add chart mode state and top-bar toggle plumbing.
2. In [app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt](app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt), add a lightweight Home UI state flow for bar-chart visibility (session-only, not persisted), plus methods to toggle/set it. Keep this separate from persisted `FilterState`.
3. In [app/src/main/java/com/example/expensetracker/MainActivity.kt](app/src/main/java/com/example/expensetracker/MainActivity.kt), add a Home-only `TopAppBar.actions` icon button (vertical bar chart icon) to toggle chart mode through ViewModel. Keep existing drawer/menu actions intact.
4. In [app/src/main/java/com/example/expensetracker/ui/TestTags.kt](app/src/main/java/com/example/expensetracker/ui/TestTags.kt), add stable test tags for the new top-bar icon and the new chart container/elements.
5. In [app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt](app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt), collect the new chart mode flow and insert chart section directly below filter chips and above `TabRow`.

6. Phase 2: Implement reusable period/timeline math and aggregation.
7. In [app/src/main/java/com/example/expensetracker/data/FilterState.kt](app/src/main/java/com/example/expensetracker/data/FilterState.kt), add helper extensions/utilities for supported chart grain types (Day/Week/Month/Year only):
- normalize to period start
- step previous/next period
- produce a 5-period window centered on selected period when possible
- cap right side so no period starts after current system period
- label formatting for axis/dropdown display
8. In [app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt](app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt), add aggregation methods that compute per-period totals for both filtered expenses and filtered incomes using existing currency conversion logic (`getAmountInCurrentDefault`). Return a model containing:
- period identity/range + display label
- expense total
- income total
- delta total and direction (profit/loss)
- missing-rate flag per period/series
9. Ensure aggregation applies existing non-time filters as already represented by `filteredExpenses`/`filteredIncomes`, while recomputing across neighboring periods by reapplying the same account/category/text conditions with overridden period windows.

10. Phase 3: Build chart UI component and interactions.
11. In [app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt](app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt), add a dedicated composable for the new vertical bar timeline section:
- top row dropdown showing current chart grain (Day/Week/Month/Year)
- optional left/right controls or horizontal pager/list for sliding periods
- 5 period groups on X-axis, each with 3 proportional vertical bars (Income, Expense, Profit/Loss)
- tap behavior per bar with selected-value annotation shown above tapped bar
- accessibility semantics/test tags for bars and selected value
12. Reuse existing dialogs from [app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt](app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt) (`DayPickerDialog`, `WeekPickerDialog`, `MonthPickerDialog`, `YearPickerDialog`) when changing grain/period from the dropdown.
13. In [app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt](app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt), wire interactions:
- chart activation: if `timeFilter` is None or AllTime, set `TimeFilter.Month(current)` before first render
- if time filter already Day/Week/Month/Year, keep it unchanged
- if current filter is Period, map to nearest supported grain per product decision (excluded from dropdown)
- bar tap on Income/Expense: set active time filter to tapped period, reveal value above bar, switch tab to Income/Expense
- bar tap on Profit/Loss: set active time filter to tapped period, reveal value only; no tab switch
- format Loss with leading minus sign
14. Keep chart-only transient selection (selected bar/value reveal) in `rememberSaveable` at HomeScreen scope; clear when chart deactivates, but do not reset current active `timeFilter`.

15. Phase 4: Synchronization and edge-case handling.
16. Add guard logic in [app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt](app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt) so sliding left/right updates `timeFilter` to previous/next period of current grain and triggers list refresh automatically via existing filtered flows.
17. Disable right-slide when next period would exceed current system period.
18. Handle zero-data periods with zero-height bars and stable labels.
19. Handle missing exchange-rate periods by showing a non-crashing fallback state consistent with existing `TotalState.RateMissing` pattern.
20. Ensure behavior is consistent across all tabs since chart is above `TabRow`; only tab switching on tap is conditional by bar type.

21. Phase 5: Strings, icons, and visual consistency.
22. Add required string resources in [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml) for icon descriptions, dropdown labels, bar legends, selected-value announcements, and empty/missing-rate messages.
23. Choose a Material icon representing vertical bars and keep selected/unselected tint behavior consistent with existing diagram toggles.

24. Phase 6: Tests and verification.
25. Extend [app/src/androidTest/java/com/example/expensetracker/ui/HomeDiagramUiTest.kt](app/src/androidTest/java/com/example/expensetracker/ui/HomeDiagramUiTest.kt) or add a focused Home bar chart UI test file to cover:
- top-bar icon visibility on Home route only
- toggle on/off persistence within session navigation
- auto-set to current month when initial filter is None/AllTime
- no auto-change when filter is already Day/Week/Month/Year
- render of 5 groups x 3 bars
- tap Income/Expense bar => value reveal + tab switch + timeFilter update
- tap Profit/Loss => value reveal only + no tab switch
- loss display includes leading minus sign
- slide prev/next updates time filter and list content
- right boundary cannot go beyond current period
26. Add unit tests under [app/src/test/java/com/example/expensetracker/viewmodel](app/src/test/java/com/example/expensetracker/viewmodel) for period stepping/window capping and per-period aggregation totals with mixed currencies/missing-rate scenarios.

**Relevant files**
- [app/src/main/java/com/example/expensetracker/MainActivity.kt](app/src/main/java/com/example/expensetracker/MainActivity.kt) — top app bar action icon wiring (Home-only).
- [app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt](app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt) — chart placement, state orchestration, dialog reuse, interactions.
- [app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt](app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt) — new vertical bar chart composable + interaction UI.
- [app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt](app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt) — session chart mode state + period aggregation methods.
- [app/src/main/java/com/example/expensetracker/data/FilterState.kt](app/src/main/java/com/example/expensetracker/data/FilterState.kt) — period stepping/window helpers for Day/Week/Month/Year.
- [app/src/main/java/com/example/expensetracker/ui/TestTags.kt](app/src/main/java/com/example/expensetracker/ui/TestTags.kt) — new stable test tags.
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml) — labels/content descriptions.
- [app/src/androidTest/java/com/example/expensetracker/ui/HomeDiagramUiTest.kt](app/src/androidTest/java/com/example/expensetracker/ui/HomeDiagramUiTest.kt) — UI behavior regression coverage.
- [app/src/test/java/com/example/expensetracker/viewmodel](app/src/test/java/com/example/expensetracker/viewmodel) — new/updated unit tests for helper math and aggregation.

**Verification**
1. Run unit tests for viewmodel/filter helper additions: targeted `gradlew test` scope for updated/new test classes.
2. Run Home UI instrumentation suite covering diagram/chart interactions: targeted `gradlew connectedAndroidTest` scope for Home chart tests.
3. Manual validation on emulator/device:
- Toggle icon appears only on Home top bar.
- With None/AllTime filter, activating chart sets current month automatically.
- Dropdown supports only Day/Week/Month/Year and opens existing pickers.
- 5 period groups render, no future period shown.
- Sliding left/right updates active time filter and transaction lists.
- Tap Income/Expense switches to corresponding tab and reveals value.
- Tap Profit/Loss reveals value only.
- Loss values render with leading `-`.
- Deactivating chart preserves current active time filter.

**Decisions**
- Render model: 5 period groups × 3 bars (Income, Expense, Profit/Loss).
- Auto-set rule: chart activation auto-sets current month only when time filter is None or AllTime.
- Top bar icon scope: Home route only.
- Dropdown scope: Day/Week/Month/Year only (Period removed for chart mode).
- Bar tap behavior: tapped period becomes active `timeFilter`; Income/Expense bars navigate tabs, Profit/Loss does not.
- Persistence boundary: chart visibility/selection is session-only; active filters continue using existing persisted `FilterState` behavior.

**Further Considerations**
1. If current persisted filter is `Period` when chart is activated, map to Month anchored by period start as deterministic fallback, or show a one-time inline hint asking user to pick Day/Week/Month/Year.
2. Keep old per-tab category/keyword diagram code independent to avoid regressions; gate each diagram mode with explicit UI state to prevent overlapping visuals.
