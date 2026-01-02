## Goal
Refactor all monetary values (amounts, balances, transfer amounts) from `Double` to `BigDecimal` across the app (Room entities, DAOs, repositories, ViewModel logic, voice parsing, UI parsing/formatting, and navigation), and replace `.fallbackToDestructiveMigration()` with an explicit Room migration that preserves existing user data.

## Current State (from quick scan)
- Room DB: `AppDatabase.kt`
  - `@Database(version = 11, exportSchema = false)`
  - Entities: `Expense`, `Account`, `Category`, `Currency`, `Keyword`, `TransferHistory`
  - `@TypeConverters(Converters::class)` present
  - Builder uses `.fallbackToDestructiveMigration()`
  - No `Migration` objects registered
- Converters: only `Date <-> Long` (no BigDecimal)
- Money fields currently `Double`:
  - `Account.balance: Double`
  - `Expense.amount: Double`
  - `TransferHistory.amount: Double`
- Navigation currently passes amount as float:
  - `navArgument("amount") { type = NavType.FloatType }`

## Inventory: Where `Double` is used for money
### Entities
- `Account` (`accounts` table)
  - `balance: Double`
- `Expense` (`expenses` table)
  - `amount: Double`
- `TransferHistory` (`transfer_history` table)
  - `amount: Double`

### ViewModel / Logic
- `ExpenseViewModel` (core money logic)
  - Expense flow:
    - `insertExpense(...)`: adjusts account `balance +/- expense.amount`
    - `deleteExpense(...)`: restores account balance
    - `updateExpense(...)`: revert old effect then apply new effect (including account changes)
  - Transfer flow:
    - `processParsedTransfer(...)`: adjusts balances and inserts `TransferHistory`
    - `deleteTransfer(...)`: reverts balances, deletes transfer
    - `updateTransfer(...)`: reverts original transfer then applies updated transfer
  - Voice parsing:
    - `parseExpense` / `parseTransfer` uses `.toDoubleOrNull()`
- Parsed voice models:
  - `ParsedExpense.amount: Double`
  - `ParsedTransfer.amount: Double`

### UI / Input
- Many screens parse input via `.toDoubleOrNull()` and serialize via `.toString()`.
- Display commonly uses string interpolation (e.g., `"Balance: ${it.balance}"`).

## Target Design Decisions
### 1) Storage representation in Room
**Recommended**: store `BigDecimal` as `TEXT` (canonical decimal string).
- Pros: preserves decimal precision; avoids SQLite REAL rounding.
- Cons: SQL numeric aggregation gets harder; likely prefer aggregations in Kotlin anyway.

Alternative (stronger currency correctness): store money as `LONG` minor-units with currency-aware scale.
- Pros: deterministic and fast for aggregation.
- Cons: requires careful per-currency scaling and wider refactor.

Non-goal option (not recommended): keep column type REAL and convert `Double <-> BigDecimal`.
- This keeps floating-point persistence and undercuts the reason for the change.

### 2) Rounding/scale policy
Define a consistent policy used everywhere:
- Determine scale via currency (ISO `defaultFractionDigits`) with fallback.
- Choose explicit `RoundingMode` (e.g., `HALF_UP`).
- Ensure balances/amounts are normalized after operations.

### 3) Formatting & parsing
- Replace `.toString()` with a consistent monetary formatter.
- Replace `.toDoubleOrNull()` with `BigDecimal` parsing.
- Decide acceptable input formats (dot decimal; optionally accept comma depending on locale requirements).

### 4) Navigation
Stop using `NavType.FloatType` for amount.
- Prefer passing amounts as `String` query args, or passing an ID and fetching amount from DB.

## Implementation Steps
### Step A — Add BigDecimal converters
- Update `Converters` to include:
  - `BigDecimal <-> String` (store as `TEXT`)
- Ensure `AppDatabase` is annotated with `@TypeConverters(Converters::class)` (already is).

### Step B — Change entity fields to BigDecimal
- Update:
  - `Account.balance: BigDecimal`
  - `Expense.amount: BigDecimal`
  - `TransferHistory.amount: BigDecimal`
- Update any constructors/defaults and any `copy(...)` call sites.

### Step C — Update all money operations to BigDecimal
- In `ExpenseViewModel`:
  - Replace `+/-` on doubles with `BigDecimal.add/subtract`.
  - Ensure all balance updates apply normalization (scale + rounding) after operations.
  - Confirm transfer logic still creates/updates the two linked `Expense` rows with `transferId` correctly.
- In voice parsing:
  - Replace `.toDoubleOrNull()` with `BigDecimal` parsing.
  - Maintain existing command grammar, just change numeric handling.

### Step D — Update UI input + output
- Replace all `toDoubleOrNull()` input parsing with `BigDecimal` parsing.
- Replace display `toString()` with formatter output.
- Validate edit screens preserve previous value when input is blank/invalid.

### Step E — Replace destructive migration with explicit migration
- In `AppDatabase.kt`:
  - Remove or avoid relying on `.fallbackToDestructiveMigration()`.
  - Bump DB version: `11 -> 12`.
  - Register a `Migration(11, 12)`.

#### Migration approach (SQLite-safe, preserves data)
Because SQLite cannot truly `ALTER COLUMN` type, use create/copy/rename:
1. Create `accounts_new` with `balance TEXT NOT NULL`.
2. Copy from `accounts`:
   - `balance` conversion from REAL to TEXT using `printf('%.17g', balance)` (or `CAST(balance AS TEXT)`; `printf` is safer for round-trip).
3. Repeat for `expenses_new.amount` and `transfer_history_new.amount`.
4. Drop old tables, rename `*_new` to original.
5. Recreate indices/constraints.

### Step F — Validation
- Verify build compiles.
- Run a migration test (instrumented) or a simple startup sanity test:
  - Confirm existing DB upgrades without wiping.
  - Confirm balances and amounts match previous displayed values.
- Spot-check:
  - Expense insert/update/delete
  - Transfer insert/update/delete
  - Voice entry for expense/transfer
  - Edit screens parsing/formatting

## Risks / Edge Cases
- Existing REAL values are already floating-point; migration can preserve the stored value, but cannot reconstruct the original intended decimal in all cases.
- `BigDecimal.toString()` can emit scientific notation; must format explicitly.
- Locale: current parsing likely assumes '.'; if users input ',', parsing may fail unless handled.
- Navigation float arg must be removed to prevent precision loss.

## File/Area Checklist
- `app/src/main/java/.../data/AppDatabase.kt` (version, migrations, builder)
- `app/src/main/java/.../data/Converters.kt` (BigDecimal converters)
- Entities:
  - `Account.kt`
  - `Expense.kt`
  - `TransferHistory.kt`
- DAOs:
  - `AccountDao.kt`
  - `ExpenseDao.kt`
  - `TransferHistoryDao.kt`
- Repos:
  - `AccountRepository.kt`
  - `ExpenseRepository.kt`
  - `TransferHistoryRepository.kt`
- ViewModel:
  - `ExpenseViewModel.kt` (all money math + voice parsing)
- UI screens:
  - Add/Edit expense
  - Add/Edit transfer
  - Account management
- Navigation:
  - `NavGraph.kt` amount argument changes

## Acceptance Criteria
- No money field persists as `Double` in Room entities.
- All money math uses `BigDecimal` with explicit rounding/scale.
- App upgrades existing DB (v11) to new schema (v12) without data loss.
- No navigation route passes money as float/double.
- UI and voice flows can input and display amounts reliably.
