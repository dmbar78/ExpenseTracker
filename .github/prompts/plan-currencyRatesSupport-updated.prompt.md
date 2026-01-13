## Plan: Currency Rates + Default Currency (Refined)

Add a Settings screen to choose a default currency (persisted via DataStore). Introduce a Room `ExchangeRate` table storing day-based FX rates for arbitrary currency pairs. Extend `Expense` and `TransferHistory` to snapshot conversion into the “original default currency” at transaction time. When showing totals later, rebase those snapshots into the current default currency using historical rates for each transaction date; if any required rate is missing (especially offline), show a “rate missing” state instead of a partial/incorrect total.

### Steps
1. Add `settings` destination + drawer item in `AppDrawer.kt` and the app nav graph (next to `home/accounts/categories/currencies`).
2. Build `SettingsScreen` with “Default currency (EUR)” selector using a `defaultCurrencyCode` DataStore flow; initialize to `EUR` on first run.
3. Add Room pair-based table `ExchangeRate` with `(date, baseCurrencyCode, quoteCurrencyCode, rate)` and DAO methods for get-or-insert by `(date, base, quote)`; treat `X→X` as 1 (either stored explicitly or handled implicitly in DAO/repository).
4. Extend `Expense` and `TransferHistory` with snapshot fields:
   - `originalDefaultCurrencyCode` (default currency at save time)
   - `exchangeRateToOriginalDefault` (txnCurrency → originalDefault at txn date)
   - `amountInOriginalDefault` (txn amount expressed in original default)
   For `TransferHistory` (same-currency-only), base these on the source account currency/amount.
5. Define backfill + maintenance behavior:
   - Migration backfill stage: set `originalDefaultCurrencyCode` for legacy rows to the current default at migration time; compute snapshot fields where rates exist; leave missing values null/zero to be filled later.
   - On app start and on default-currency change: enqueue a “rates needed” reconciliation that fetches missing rates and fills missing snapshot fields; continue until completed (best-effort while app is running; optionally upgrade to WorkManager later for true background completion).
6. Update UI calculations:
   - Accounts: show `balance (converted)` only when account currency ≠ current default; conversion uses `rate(accountCurrency → currentDefault, today)` (fetch/create if missing; show “rate missing” if unavailable).
   - Home totals (inside active tab, centered above list): for each record compute:
     - if `currentDefault == originalDefaultCurrencyCode`: use `amountInOriginalDefault`
     - else: `amountInOriginalDefault * rate(originalDefaultCurrencyCode → currentDefault, txnDate)`
     Show totals only when all required snapshot fields and rebase rates are available; otherwise show “rate missing”.
7. Implement `RatesProvider` + `ExchangeRateRepository` abstraction; start with offline placeholder that returns no rates. Wire “fetch missing rate” into repository and call from UI/VM when needed (later swap in Retrofit + WorkManager). Design ExchangeRateRepository.reconcileRatesNeeded() to be callable from both UI/ViewModel and a future Worker.

### Further Considerations
1. Naming: prefer `amountInOriginalDefault` over `amountInDefault` to prevent double-conversion bugs.
2. Rate coverage: “required rates available” means both txnCurrency → originalDefault (for snapshots) and originalDefault → currentDefault (for rebasing) for each transaction date.
3. Reliability: if you truly need “silently run until finished even if app is closed”, plan a WorkManager sync once a real network provider exists.
