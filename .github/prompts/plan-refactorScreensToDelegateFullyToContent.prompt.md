## Plan: Refactor Screens to Delegate Fully to *Content
Refactor AddAccountScreen, AddCategoryScreen, EditExpenseScreen, EditTransferScreen into thin wrappers: collect ViewModel flows, handle savedStateHandle results + navigation callbacks, and render only the corresponding *ScreenContent composables. This eliminates duplicated UI logic between real app screens and test-only content, so features like “Create New…” dropdown items are implemented once and validated by existing content tests.

### Steps
1. Convert Add screens to wrappers calling Content in AddCategoryScreen.kt and AddAccountScreen.kt, using AddCategoryScreenContent.kt and AddAccountScreenContent.kt.
2. Keep side-effects in the wrappers: errorFlow dialogs + navigateBackFlow pop, and set createdCategoryName via savedStateHandle (same behavior as today) in AddCategoryScreen.kt.
3. Refactor EditExpenseScreen into a wrapper over Content: map nav args → EditExpenseState, pass lists from ViewModel, and implement callbacks for save/delete/category-create navigation in EditExpenseScreen.kt calling EditExpenseScreenContent.kt.
4. Preserve savedStateHandle result plumbing in the wrapper: keep reading + applying + clearing createdCategoryName (and later createdAccountName) in EditExpenseScreen.kt.
5. Refactor EditTransferScreen into a wrapper over Content: map nav args → EditTransferState, pass accounts, and implement callbacks for save/delete/date click in EditTransferScreen.kt calling EditTransferScreenContent.kt.
6. Align behavior gaps between Screen and Content: update Content state/callbacks to support the current UX (snackbar/error messages, case-insensitive validation/currency inference) so delegating doesn’t regress behavior; then update/extend VoiceFlowContentTest.kt accordingly and keep tags stable via TestTags.kt.
7. Remove all duplicated UI logic from the Screen files, ensuring all UI state + validation + error visibility is driven by the Content composables.
8. Pop EditExpenseScreen only when save succeeds after pressing save button (i.e., after the ViewModel completes successfully). Show errorFlow if it fails.
9. Ensure that EditTransferScreen continue to use snackbars from errorFlow; Content will need a callback mechanism to emit messages without owning a SnackbarHostState.
10. Maintain existing test coverage: ensure all existing Compose UI tests in VoiceFlowContentTest.kt pass without modification, confirming that the refactor preserves behavior.
11. Keep existing dropdown option test tags unchanged (accountOption_{id}, accountOption_source_{id}, accountOption_dest_{id}) to avoid breaking tests like VoiceFlowContentTest.kt.