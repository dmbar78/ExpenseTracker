## Plan: Remove Transfer Comment Parsing

Remove voice “comment …” parsing from `ExpenseViewModel.parseTransfer`, while keeping transfer recognition and amount/account parsing working as before; ensure downstream code stays consistent (comment becomes always `null` for voice transfers).

### Steps 3–6
1. Update `parseTransfer` in `app/src/main/java/com/example/expensetracker/viewmodel/ExpenseViewModel.kt` to stop using `commentRegex` and `commentMatch`.
2. Derive `destAccountName` only from the text before the amount match; always return `ParsedTransfer(..., comment = null)`.
3. Remove the unused local `commentRegex` and any comment-related branching in `parseTransfer`.
4. Update the voice help/error string in `onVoiceRecognitionResult` to remove `Comment <Comment>` from the transfer format guidance.
5. Confirm `processParsedTransfer` still saves `TransferHistory(comment = null)` and `EditTransferScreen` continues to handle null comments safely (it already uses `it.comment ?: ""`).

### Further Considerations 1
1. If you still want typed-in transfer comments, keep them as UI-only (don’t mention voice comments in prompts).