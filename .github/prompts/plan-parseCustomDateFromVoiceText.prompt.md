## Plan: Parse Custom Date From Voice Text

Add optional date parsing at the *end* of `spokenText` for both voice flows, convert it to epoch millis (current year), and store it in `ParsedExpense.expenseDate` / `ParsedTransfer.transferDate` so `processParsedExpense` / `processParsedTransfer` persist the intended date and `formatDate(millis)` can display it.

### Steps 1–6
1. Extend `ParsedExpense` and `ParsedTransfer` (in `VoiceRecognitionState.kt`) to include a `Long` date field (millis): reuse existing `expenseDate` for expenses and add `transferDate` for transfers.
2. Add a helper in `ExpenseViewModel.kt` to parse a trailing date phrase and return `(strippedText, dateMillis?)` (e.g., `parseTrailingSpokenDate(...)`).
3. Implement date token support in the helper for endings like: month name + day (`January 1`, `January 1st`) and day + “of” + month (`1st of January`), including ordinal words (`First` … `Thirty First`).
4. Wire the helper into `parseExpense`: treat everything after `category <CategoryName>` as an optional date; set `ParsedExpense(expenseDate = parsedOrDefault)`.
5. Wire the helper into `parseTransfer`: treat everything after the amount as an optional date; set `ParsedTransfer(transferDate = parsedOrDefault, comment = null)`.
6. Update `processParsedTransfer` to pass the parsed millis into the `TransferHistory` record (either via an explicit field, or by relying on a default only when no date was spoken), and update success messages to use `formatDate(parsedMillis)` where applicable.

### Further Considerations 1–3
1. Default behavior when no date is spoken: use “today” (`System.currentTimeMillis()`) consistently.
2. Locale: month-name parsing should be locale-aware to match voice language.
3. If `TransferHistory` currently has an implicit date default, override it when a date is parsed to avoid silently ignoring spoken dates.
