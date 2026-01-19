## Plan: Trim Text Input Before Search/DB

Normalize user-entered text fields by trimming leading/trailing whitespace before (a) running in-memory search/filtering and (b) persisting new/updated values to Room. Enforce this centrally in ViewModels (single source of truth), and optionally mirror trimming at UI “OK/Save” for better UX.

### Steps {3–6 steps, 5–20 words each}
1. Add `normalizeText()` helpers in ExpenseViewModel and other relevant ViewModels (accounts/categories/currencies/keywords).
2. Trim query text before filtering: update `setTextQueryFilter()` and keyword dropdown search (`keywordQuery.trim()`).
3. Trim names before DB writes: normalize `insertAccount`, `insertCategory`, `insertCurrency`, `insertKeyword` and update equivalents.
4. Trim navigation-prefill strings: normalize `onCreateNewAccount(category/account)` args before navigating.
5. Ensure uniqueness isn’t bypassed: always write trimmed values to `name` fields with unique indexes.
6. Keep “OK with empty acts as Cancel”: after trim, treat blank as null/no-op.
7. Do not trim/collapse “internal spaces”: trim edges only.
8. Comment fields are not trimmed: preserve exact user formatting.
