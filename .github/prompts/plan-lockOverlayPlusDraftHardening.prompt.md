## Plan: Lock Overlay Plus Draft Hardening

Separate the work into two tracks. Track A fixes the root cause for route loss across the whole app by keeping the NavHost alive during lock. Track B then hardens unsaved local screen state in priority order, starting with the screens most exposed to data-loss flows: EditExpenseScreen first, then EditTransferScreen, then the remaining add and edit forms.

**Track A: Global Navigation Preservation**

1. Refactor MainActivity so MainContent always stays composed and the lock UI is shown as a blocking overlay instead of replacing the app content.
2. Keep the existing timeout checks, but render the lock screen above MainContent in a parent container so the current NavHost and back stack stay alive during lock.
3. Verify route preservation against NavGraph, specifically for accounts, categories, currencies, settings, expenses, transfers, and nested destinations.
4. Add a regression test that navigates away from home, triggers lock, unlocks, and confirms the app is still on the same destination rather than restarting at home.

**Track B: Screen-by-Screen Draft Hardening Priority**

1. Priority 1: Harden EditExpenseScreen and EditExpenseScreenContent. This remains first because it combines unsaved form state, camera handoff, timeout, and content-layer duplicate state.
2. Priority 2: Review and close remaining gaps in EditTransferScreen. It is already mostly hardened with rememberSaveable, so this is the next fastest high-value win.
3. Priority 3: Harden plain-remember input forms in this order: AddAccountScreen, EditAccountScreen, AddCategoryScreen, EditCategoryScreen, and AddCurrencyScreen.
4. Priority 4: Review SettingsScreen and HomeScreen only for costly interrupted flows such as pending restore, export, or other state the user would reasonably expect to survive.

**Why EditTransferScreen Is Second**

EditTransferScreen already persists the main transfer draft fields with rememberSaveable: source account, destination account, amounts, currency, date, comment, error flags, lastCreateNewTarget, and hasInitialized. That makes it much closer to complete than the account, category, and currency forms. Its remaining work is mainly to confirm there is no duplicate local draft ownership in its content layer and no non-saveable dialog state that can still produce visible data loss after unlock.

**Verification**

1. Global route-preservation test for Track A.
2. Expense draft plus camera timeout test for Track B Priority 1.
3. Transfer draft preservation test for Track B Priority 2.
4. Incremental form-draft tests as each add or edit screen is hardened.
