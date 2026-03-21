## Plan: Fix No-Keyword Diagram Drill-Down

The fix will treat the no-keyword bar as a special drill-down case, not as text search. You selected temporary drill-down only, so no new persisted filter/chip will be introduced.

**Steps**
1. Define one canonical no-keyword bucket identity at the breakdown source in [app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt](app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt#L1051), instead of relying on scattered literal text comparisons.
2. Add typed metadata to keyword breakdown entries in [app/src/main/java/com/example/expensetracker/viewmodel/DiagramModels.kt](app/src/main/java/com/example/expensetracker/viewmodel/DiagramModels.kt#L27) so the UI can detect no-keyword buckets without depending on display label text.
3. Add a view-model drill-down helper in [app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt](app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt#L474) that:
1. For normal keywords, returns items whose attached keyword list contains that keyword.
2. For no-keyword bucket, returns items with zero attached keywords.
4. Replace current keyword click routing in [app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt](app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt#L297) and [app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt](app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt#L321) so it no longer calls text query directly.
5. Introduce temporary per-tab drill-down list state in [app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt](app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt#L149), keeping Expenses and Incomes independent.
6. Keep lifecycle cleanup tight in [app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt](app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt#L213): reset temporary drill-down when category/drill-down context changes, diagram closes, or filters are reset.
7. Ensure diagram callback path remains compatible in [app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt](app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt#L1453), while preserving existing chart rendering and tap tags.

**Relevant files**
- [app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt](app/src/main/java/com/example/expensetracker/ui/screens/HomeScreen.kt)
- [app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt](app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt)
- [app/src/main/java/com/example/expensetracker/viewmodel/DiagramModels.kt](app/src/main/java/com/example/expensetracker/viewmodel/DiagramModels.kt)
- [app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt](app/src/main/java/com/example/expensetracker/ui/components/FilterComponents.kt)
- [app/src/test/java/com/example/expensetracker/viewmodel/ExpenseViewModelTest.kt](app/src/test/java/com/example/expensetracker/viewmodel/ExpenseViewModelTest.kt)
- [app/src/androidTest/java/com/example/expensetracker/ui/HomeDiagramUiTest.kt](app/src/androidTest/java/com/example/expensetracker/ui/HomeDiagramUiTest.kt)

**Verification**
1. Unit test: verify no-keyword drill-down returns only items with no keyword links.
2. Unit test: verify normal keyword drill-down still works and is not persisted as text query.
3. UI test: tap a no-keyword bar and verify list narrows to matching transactions instead of empty due to literal text search.
4. Manual regression: text query dialog/chip behavior remains unchanged and still persistent/removable as before.

**Decisions**
- Chosen UX: temporary drill-down only.
- Included: expenses/incomes category→keyword diagram click behavior.
- Excluded: FilterState/DataStore schema expansion and transfer filter changes.
