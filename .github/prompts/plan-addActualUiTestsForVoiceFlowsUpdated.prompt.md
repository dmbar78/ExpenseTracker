## Plan: Add Real Compose UI Tests (Voice Flows) - IMPLEMENTED

Add stable Compose selectors (testTag contracts) plus small "content extraction" seams so Compose UI tests can drive voice-derived screens deterministically (no SpeechRecognizer, no real DB). Split coverage into (A) pure content tests for validation + callback arguments, and (B) thin navigation/wiring tests for the "Create New…" + savedStateHandle roundtrip and the voice dialogs.

### Implementation Status: ✅ COMPLETE

**Files Created:**
- `app/src/main/java/com/example/expensetracker/ui/TestTags.kt` - Stable testTag constants
- `app/src/main/java/com/example/expensetracker/ui/screens/content/EditExpenseScreenContent.kt` - Pure UI composable + state/callbacks
- `app/src/main/java/com/example/expensetracker/ui/screens/content/AddCategoryScreenContent.kt` - Pure UI composable + state/callbacks
- `app/src/main/java/com/example/expensetracker/ui/screens/content/AddAccountScreenContent.kt` - Pure UI composable + state/callbacks
- `app/src/main/java/com/example/expensetracker/ui/screens/content/EditTransferScreenContent.kt` - Pure UI composable + state/callbacks
- `app/src/main/java/com/example/expensetracker/ui/screens/content/TransferDisambiguationDialogContent.kt` - Pure UI composable + state/callbacks
- `app/src/androidTest/java/com/example/expensetracker/ui/VoiceFlowContentTest.kt` - Content tests for flows 2-18

**Files Modified (testTags added):**
- `EditExpenseScreen.kt` - Added testTags for all interactive elements
- `AddCategoryScreen.kt` - Added testTags for all interactive elements
- `AddAccountScreen.kt` - Added testTags for all interactive elements
- `EditTransferScreen.kt` - Added testTags for all interactive elements
- `VoiceRecognitionDialogs.kt` - Added testTags for all dialog elements
- `app/build.gradle.kts` - Added navigation-testing dependency

### Flows
Note: balance adjustments + ledger correctness are already covered by Room/DAO tests; UI tests here assert (1) correct prefill, (2) correct error visibility/clearing, (3) Save/Delete are blocked or invoke the right callbacks with the right arguments (including date millis).

0) AddAccount: create account Test0 (RUB, balance 100) via AddAccount content test (fields + Save callback args).
1) AddAccount: create accounts Test1/Test2/Test3 (EUR, balance 100) via AddAccount content tests (or provide them as seeded lists for downstream tests).

2) Parsed expense (Expense): “expense from test1 20 category default”
- EditExpense opens with account=Test1, amount=20, category=default, currency=EUR.
- Save calls insert-expense callback once with expected args.
- Date: assert saved date millis is derived from injected nowMillis (don’t assert DatePicker UI).

3) Parsed expense (Expense) with unknown category + spoken date: “expense from test2 20 category test January First”
- EditExpense opens with account=Test2, amount=20, category text “test”, categoryNotFound error visible.
- Category menu → “Create New…” navigates to AddCategory with name prefilled “test”.
- Saving category returns to EditExpense, category error clears, Save now succeeds (insert-expense callback args include the new category).
- Date: assert saved date millis equals the expected millis under a fixed clock/timezone.

4) Parsed income (Income): “income to test1 20 category default”
- EditExpense opens in Income mode with account=Test1, amount=20, category=default, currency=EUR.
- Save calls insert-expense callback once with expected args.

5) Parsed income (Income) with unknown category + spoken date: “income to test2 20 category income First of January”
- Same as flow 3 but Income mode; category prefill is “income”.
- Date: assert saved date millis equals expected millis under a fixed clock/timezone (don’t assert platform picker text).

6) Parsed transfer with currency mismatch (UI layer): “transfer from test0 to test1 20”
- EditTransfer/transfer UI shows currencyMismatch error and blocks Save (callback not invoked).

7) Parsed transfer with same accounts (UI layer): “transfer from test0 to test0 20”
- EditTransfer/transfer UI shows sameAccounts error and blocks Save (callback not invoked).

8) Parsed transfer success, no spoken date: “transfer from test1 to test2 20”
- Transfer UI prefilled; Save calls insert-transfer callback once with expected args; date millis derived from injected nowMillis.

9) Parsed transfer success, spoken date: “transfer from test2 to test1 20 January One”
- Save calls insert-transfer callback once; date millis equals expected millis under fixed clock/timezone.

10) Delete transfer via EditTransfer content test
- Seed an existing transfer; press Delete invokes delete callback once with correct id.

11) Update transfer: change amount via EditTransfer content test
- Save invokes update callback once with updated amount and correct id/date.

12) Update transfer: switch accounts via EditTransfer content test
- Save invokes update callback with new source/destination ids.

13) Update transfer: change destination account via EditTransfer content test
- Save invokes update callback with destination changed only (source preserved).

14) Update expense: change amount via EditExpense content test
- Save invokes update callback with updated amount (BigDecimal).

15) Update expense: change account via EditExpense content test
- Save invokes update callback with account id changed; currency display updates accordingly.

16) Delete expense via EditExpense content test
- Delete invokes delete callback once with correct id.

17) Parsed expense with unknown account + unknown category + spoken date:
“expense from unknown 20 category test2 January First”
- EditExpense opens with account text “unknown” (accountNotFound error visible), category text “test2” (categoryNotFound error visible), currency empty/null.
- Press Save: blocked, both error tags still visible.
- Select account Test1: account error clears, currency fills to EUR.
- Category → “Create New…”: AddCategory prefilled “test2”, save returns and clears category error.
- Press Save: succeeds (insert-expense callback invoked once with account=Test1, category=newCategory, currency=EUR, date millis = expected).

18) Parsed transfer where both accounts unknown triggers VoiceRecognitionDialogs:
“transfer from unknown1 to unknown2 50 January 1”
- Accounts-not-found dialog shows unknown1/unknown2 prefilled and both notFound errors; Save blocked.
- Select valid accounts (Test2/Test3): errors clear; Save invokes insert-transfer callback with resolved ids and date millis = expected.

### Steps
1. Add stable testTag contracts across UI
- EditExpense: root, account dropdown/value, amount field, currency value, category dropdown/value, “Create New…”, date field trigger, Save/Delete, error texts/containers (accountNotFound/categoryNotFound).
- AddCategory: root, name field, Save/Cancel.
- AddAccount: root, name field, currency picker/value, balance field, Save/Cancel.
- EditTransfer: root, source/destination dropdown/value, amount field, date field trigger, Save/Delete, error tags (currencyMismatch/sameAccounts/accountsNotFound).
- VoiceRecognitionDialogs: dialog roots + source/destination selectors + Save/Cancel + per-field notFound errors.
- Dropdown items: stable tags for options (prefer ids; fall back to normalized names if ids aren’t available).

2. Extract pure UI composables for testability (content seam)
- Introduce *Content composables (EditExpenseContent, EditTransferContent, AddCategoryContent, AddAccountContent, VoiceRecognitionDialogsContent) that accept:
  - UI state (including “not found” booleans and the parsed/display strings)
  - lists (accounts/categories)
  - callbacks (onSave/onDelete/onCreateCategory/onPickAccount, etc.)
  - injected time source (nowMillis or Clock + ZoneId)

3. Implement content tests first (most coverage, least flake)
- Cover flows 2, 4, 6–16, 17 (validation + error clearing + callback args).
- Assert blocked Save by verifying callback is not invoked and error tags remain visible.

4. Add a minimal navigation/wiring test harness (only where navigation matters)
- Use TestNavHostController and a tiny NavHost with only the relevant destinations:
  - EditExpense ↔ AddCategory (savedStateHandle roundtrip)
  - VoiceRecognitionDialogs → resolved transfer action (whatever the app’s wiring is)
- Cover flows 3, 5, 17 (category creation roundtrip) and 18 (dialog resolution).

5. Keep date assertions deterministic
- Do not assert platform DatePickerDialog UI.
- Assert the date millis passed into Save/update callbacks under a fixed clock/timezone.

### Further Considerations
1. Don’t mix DB correctness into UI tests; rely on LedgerDao/Room tests for balances.
2. Keep testTag names stable; treat them as a public test API.
3. If you want true end-to-end “spokenText → ViewModel → navigation”, do that as a separate, minimal integration test layer (more brittle than UI-content tests).
