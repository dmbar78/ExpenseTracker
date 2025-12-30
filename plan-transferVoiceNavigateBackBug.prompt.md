# Plan: Fix global `navigateBackFlow` leak (voice transfer → unexpected pop)

## Problem statement
There is a navigation bug in the voice-flow sequence:

- If I perform a **Transfer transaction by voice** first, then
- Perform an **Expense transaction by voice** where **Category Not Found** happens, then
- From `EditExpenseScreen`, choose **Create New...** to navigate to `AddCategoryScreen`, then
- After saving the new category, **`EditExpenseScreen` closes unexpectedly**.

If I retry the expense transaction again, I can successfully create the new category.

This behavior points to **shared global back navigation** via `navigateBackFlow`, not to transfers themselves.

## Current hypothesis (what’s likely happening)
1. I do a voice transfer first. That:
    - Calls `processParsedTransfer`.
    - Updates accounts twice.
    - Each account update emits a global back event (`_navigateBackChannel.send(Unit)`).

2. Later I do a voice expense with unknown category:
    - `processParsedExpense` navigates to `editExpense/0?...`.

3. In `EditExpenseScreen` I choose **Create New...** to go to `AddCategoryScreen`.

4. Instead of opening `AddCategoryScreen` normally, the global back event causes `EditExpenseScreen` to close unexpectedly.

5. Some screens (notably add/edit screens) have a `LaunchedEffect` collecting `navigateBackFlow` and calling `navController.popBackStack()`.

6. Because `navigateBackFlow` is global, **unrelated/stale back events emitted earlier** (e.g., from the transfer’s account updates) can be consumed later by whatever collector is active at the time (e.g., `AddCategoryScreen`), producing extra `popBackStack()` calls and closing `EditExpenseScreen` unexpectedly.

## Confirmed by audit (facts)
### Global back emitters (`ExpenseViewModel.kt`)
These functions emit `_navigateBackChannel.send(Unit)` today:
- `updateExpense`
- `updateTransfer`
- `insertAccount`
- `updateAccount`
- `insertCategory`
- `updateCategory`
- `insertCurrency`

### Collectors (Compose screens)
These screens collect `navigateBackFlow` and call `navController.popBackStack()` via `LaunchedEffect(Unit)`:
- `AddAccountScreen`
- `EditAccountScreen`
- `AddCurrencyScreen`
- `AddCategoryScreen`
- `EditCategoryScreen`
- `EditTransferScreen`

### Voice transfer emits global back events
`onVoiceRecognitionResult` → `processParsedTransfer` currently calls `updateAccount(...)` twice.
Since `updateAccount(...)` emits `navigateBackFlow`, a single voice transfer produces **two** back events.

## Root cause
`navigateBackFlow` is being used as a global “pop back” signal, but it is also emitted by non-UI, non-screen-specific operations (notably account updates), including the **voice transfer** path.

This mixes:
- **Data-layer state changes** (update balances / save transfer)
  with
- **Navigation side effects** (pop back)

Because the event is global and collectors are screen-based, **any active collector can pop** in response to an unrelated event.

## Minimal fix (stable, low risk)
### Principle
Make “data changes” and “navigation” clearly separated.

### Change set
1. **Stop emitting global back events from data operations.**
    - In `ExpenseViewModel`, remove `_navigateBackChannel.send(Unit)` from:
        - `updateAccount` (critical for this bug)
        - `updateTransfer`
        - `updateExpense`
    - (Optional, for consistency) also remove it from:
        - `insertAccount`
        - `insertCategory` / `updateCategory`
        - `insertCurrency`

2. **Introduce explicit screen-only APIs that emit navigation.**
    - Keep `*AndNavigateBack(...)` helpers for screens that want “save then close”.
    - Example:
        - `updateAccount(account)` → data only
        - `updateAccountAndNavigateBack(account)` → data + `_navigateBackChannel.send(Unit)`

3. **Fix voice transfer path to use non-navigating updates only.**
    - In `processParsedTransfer`, update accounts using internal/no-nav methods.

4. **Remove `navigateBackFlow` completely.**
    - UI-local `navController.popBackStack()` should be used everywhere.
    - Each screen that wants to pop after save should call it directly after invoking the data operation.

## Expected behavior after fix
- A preceding voice transfer should not “queue up” any back events.
- Creating a category from `EditExpenseScreen` via “Create New...” triggers only one expected pop (back to `EditExpenseScreen`).
- Repeating the same expense flow behaves consistently regardless of transfers done before.

## Verification checklist
- [ ] Voice transfer updates balances and persists transfer without popping any screen.
- [ ] Voice expense with unknown category opens `EditExpenseScreen` correctly.
- [ ] From `EditExpenseScreen` → Create New... → `AddCategoryScreen` → Save returns to `EditExpenseScreen` (no extra pop).
- [ ] `EditTransferScreen` Save still returns (either via `updateTransferAndNavigateBack` or local pop).
- [ ] No other screen unexpectedly pops when accounts are updated.

## Open questions (for refinement)
- Do we want `navigateBackFlow` to remain at all, or move to UI-local `navController.popBackStack()` everywhere?
- Should navigation events be typed (sealed class) rather than `Channel<Unit>`?
- Long-term: should we isolate navigation channels per feature flow (accounts/categories/transfers) to prevent cross-talk?