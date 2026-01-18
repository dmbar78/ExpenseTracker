## Plan: Accounts Totals Header

Add a HomeScreen-style totals summary to the Accounts screen by computing a single “total balance in default currency” and rendering a `TotalState` header under the “Accounts / Create New” row and above the list. Reuse the same `TotalState` UI behavior (Loading / Success / Missing rates) and the same conversion semantics (any missing conversion rate → “Total unavailable (missing rates)”).

### Steps
1. Reuse Home’s `TotalState` and `TotalHeader` patterns from the Home screen implementation (e.g., `HomeScreen.kt`) for the Accounts total header UX.
2. In `AccountsScreen.kt` (locate the file in your project via IDE search if needed), add a `TotalState` state holder and compute the accounts total in a `LaunchedEffect(accounts, defaultCurrency)`.
3. Sum balances by converting each account to `defaultCurrency` using `viewModel.getAccountConversionRate(...)`; if any rate is `null`, set `TotalState.RateMissing` instead of a numeric total.
4. Render the total header directly after the header spacer (under “Accounts”) and before the empty/list branch, matching Home’s string and styling: “Total: …” or “Total unavailable (missing rates)”.
5. Reduce duplicate formatting by aligning Accounts’ `formatBalance(...)` with Home’s `formatMoney(...)` (either reuse one helper or keep Accounts’ helper but match output exactly).
6. Accounts total should treat `null` rates as “missing” (strict, Home-like).
7. When opening AccountsScreen, show Loading briefly while ensuring/fetching rates, then show total (or missing).