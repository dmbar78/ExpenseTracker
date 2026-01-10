## Plan: Make VoiceFlowContentTest Landscape-Robust

Most failures are from smaller landscape height: controls (Save/Delete) sit below the fold in LazyColumn screens, and tests click without scrolling; delete tests then fail to find “Yes” because the dialog never opened (Delete wasn’t effectively clicked). Fix by consistently scrolling to targets, waiting for popups/dialogs, and avoiding brittle text-only dialog clicks.

### Steps
1. Add `performScrollToNode` before clicking `Save`/`Delete` in `VoiceFlowContentTest.kt` using root tags `TestTags.EDIT_EXPENSE_ROOT` / `TestTags.EDIT_TRANSFER_ROOT`.
2. After opening any `ExposedDropdownMenuBox`, `waitUntil` the option tag exists before clicking (stabilizes popup timing in landscape) in `VoiceFlowContentTest.kt`.
3. Replace `onNodeWithText("Yes")` with a click-action matcher (`hasText("Yes")` + `hasClickAction()`, optionally `useUnmergedTree = true`) plus a `waitUntil` guard in `VoiceFlowContentTest.kt`.
4. Specifically harden the failing tests listed in the report (flow2/flow4/flow14/create*/editTransfer_updateAmount/flow18/delete flows) by applying steps 1–3 where they interact with bottom buttons or popups in `VoiceFlowContentTest.kt`.
5. Add confirm/dismiss test tags for delete dialogs in `TestTags.kt` and apply them to dialog buttons in `EditExpenseScreenContent.kt` and `EditTransferScreenContent.kt`, then have tests click by tag instead of “Yes”.

### Further Considerations
1. Prefer tests-only changes first; if “Yes” is still flaky, add dialog button tags (more stable, localization-proof).
2. If IME reduces height mid-test, insert `waitForIdle()` after text input before scrolling/clicking.
3. Keep existing option tags unchanged (`accountOption_*`) while adding only new tags if needed.
