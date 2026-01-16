## Plan: Switch to Frankfurter RatesProvider (Call-Minimized, EUR Pivot, UTC)

Replace the current `OfflineRatesProvider` with a Frankfurter-backed implementation using Retrofit/OkHttp and cache results in the existing Room `exchange_rates` table.

Key goals:
- No API key / no secret injection.
- Use UTC for date keys to match Frankfurter.
- Minimize calls by using a EUR pivot and batching symbols (maximize output from 1 call).

### Strategy

Frankfurter is strongest when you fetch many rates at once for a single base.

Use EUR as the pivot:
- Fetch and store only `EUR → X` rates per day.
- Derive any pair rate `A → B` using the EUR cross-rate formula:
  - If `EUR→A = rA` and `EUR→B = rB`, then `A→B = rB / rA`.
  - Special cases:
	 - `A == B` → `1`
	 - `EUR→X` → `rX`
	 - `X→EUR` → `1 / rX`

### Steps

1. Add network dependencies in `app/build.gradle.kts`:
	- Retrofit + OkHttp + one JSON converter.
	- (Optional) OkHttp logging interceptor, but avoid logging headers.

2. Define a minimal Frankfurter API client using `https://api.frankfurter.dev/v1/`:
	- Latest (latest working day): `GET /latest?base=EUR&symbols=A,B,C`.
	- Historical day: `GET /YYYY-MM-DD?base=EUR&symbols=A,B,C`.

3. Normalize the “rate day” consistently (Frankfurter dates are UTC-based) without changing what users see:
	- Keep list/date display in the user’s local timezone (no UI changes).
	- Introduce a “rate day” for each transaction: derive a calendar day (e.g., `YYYY-MM-DD`) from the transaction timestamp using the user’s timezone.
	- Store/cache using a UTC-normalized key that matches that calendar day (e.g., convert that `YYYY-MM-DD` to UTC midnight millis) so the DB key is stable and matches Frankfurter’s UTC date model.
	- When fetching “today” rates, prefer `GET /latest` and store results under the response `date` (latest available working day) to avoid timezone edge cases around midnight/weekends.

4. Implement `FrankfurterRatesProvider : RatesProvider` (new file or alongside the repository):
	- Make the main call “EUR base + batched symbols”.
	- Implement `fetchRates(baseCurrency, date)` as pivot-first:
		- Always call Frankfurter with `base=EUR` and `symbols=<needed quotes>` and return a map of `quoteCurrency -> (EUR→quote)`.
		- Treat the `baseCurrency` parameter as unused/ignored for the network layer (the pivot is always EUR).
	- Implement `fetchRate(base, quote, date)` without additional network calls when possible:
		- Derive via cached EUR quotes: if `EUR→base=rA` and `EUR→quote=rB`, then `base→quote = rB / rA`.
		- Handle special cases: `base==quote` → 1, `base==EUR` → `rB`, `quote==EUR` → `1/rA`.
  - do cross-rate math with `BigDecimal` and consistent rounding to avoid drift.
  - Weekend/holiday behavior: `/latest` returns the latest available working day; for historical totals, always request the specific `YYYY-MM-DD` day and handle missing results.

5. Make reconciliation batching the default to prevent UI fanout:
	- Update `ExchangeRateRepository.reconcileRatesNeeded()` to group missing work by `(utcDay)`.
	- For each day, build a single `symbols` set of all currencies needed for that day.
	- Do one request per day and store results with `insertAll(...)` as `(base=EUR, quote=X, rate=rX)`.
	- Add in-flight dedupe per `(utcDay)` so concurrent screens don’t trigger duplicate calls.

6. Wire the provider into repository creation (where `ExchangeRateRepository(...)` is constructed).
  - Ensure UI totals/balances do not accidentally trigger per-item network calls; prefer “prefetch/reconcile once, then read cache”.
7. if Frankfurter doesn’t return a currency, treat it as “missing rate” and keep UI totals in the “rate missing” state.