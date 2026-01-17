## Plan: Currency UX + Manual Override + Fallback Rates (+ Default Currency Guard)

Update the currency UI so users can clearly see the default currency, tap a currency to edit it, and manage exchange-rate overrides (including for currencies Frankfurter doesn’t support). Adjust totals logic so transactions use the most recent cached rate on or before the transaction date (Option A), while account balances keep using “latest available”. Add a default-currency guard in Settings so selecting a new default currency cannot proceed unless the required `EUR → newDefault` pivot exists (or the user manually enters it). This keeps conversions consistent with the EUR-pivot model and avoids “default changed but totals break”.

### Steps
1. Update the currencies list UI in `app/src/main/java/com/example/expensetracker/ui/screens/CurrenciesScreen.kt` to observe `defaultCurrencyCode` from `ExpenseViewModel` and show a “Default” marker on the matching row.
2. Change currency navigation in the nav graph (e.g., add an edit route; prefer the existing “id=0 create” pattern), and make currency rows clickable; remove the per-row Delete action from the list.
3. Reuse the create screen as create/edit in [app/src/main/java/com/example/expensetracker/ui/screens/AddCurrencyScreen.kt](../../app/src/main/java/com/example/expensetracker/ui/screens/AddCurrencyScreen.kt): when editing, prefill fields; show Delete only in edit mode; route back to list on save/delete.
4. Add ViewModel support for currency edit + rate info: add `loadCurrency(currencyId)` + `selectedCurrency` flow in `ExpenseViewModel`, and a helper to compute/display “latest rate” as “1 {currency} = X {default}” (read-only).
5. Implement exchange-rate lookups needed by UX + totals:
   - Add repository helpers in [app/src/main/java/com/example/expensetracker/data/ExchangeRateRepository.kt](../../app/src/main/java/com/example/expensetracker/data/ExchangeRateRepository.kt) to fetch (a) latest rate and (b) most recent on-or-before a date using the existing DAO method in [app/src/main/java/com/example/expensetracker/data/ExchangeRateDao.kt](../../app/src/main/java/com/example/expensetracker/data/ExchangeRateDao.kt).
   - Update totals code paths in `ExpenseViewModel` so expenses/transfers use “on-or-before” (Option A), while account conversions use “latest”.
6. Add the override flow on the currency edit screen:
   - If a “latest” rate can be shown, show it read-only as “1 {currency} = X {default}”.
   - Add an “Override rate” action that writes the correct pivot rows into Room via `ExchangeRateRepository.setRate(...)` with the rule: store the EUR-pivot row `EUR → currency` for “today” (normalized day key), so all cross-rate derivations remain consistent.
7. Add a default-currency guard in Settings when changing the default currency:
   - In the default currency picker dialog in [app/src/main/java/com/example/expensetracker/ui/screens/SettingsScreen.kt](../../app/src/main/java/com/example/expensetracker/ui/screens/SettingsScreen.kt), when the user selects a candidate new default currency, check whether the pivot `EUR → candidateDefault` exists (use “most recent on-or-before today”).
   - If the pivot exists: allow the candidate currency to remain selected normally.
   - If the pivot does not exist: open a popup (AlertDialog) prompting the user to manually enter the missing pivot rate as “1 EUR = X {candidateDefault}”. If Frankfurter supports the currency but cache is empty: the guard shouldattempt a fetch first before prompting.
     - the popup should accept both “,” and “.” decimal separators (reuse existing money parsing behavior)
     - OK: validate `X > 0`; insert/update the pivot row into `exchange_rates`; close the popup; return to the default-currency picker with the candidate currency selected but NOT persisted yet.
     - Cancel: close the popup; return to the default-currency picker with the previous currency still selected (do not change selection, do not persist).
   - Only persist the new default currency (`setDefaultCurrency(...)`) when the user confirms the default-currency picker, after the pivot requirement is satisfied.