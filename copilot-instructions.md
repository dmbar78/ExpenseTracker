# ExpenseTracker AI Instructions

## Project Overview
ExpenseTracker is a modern Android application built with **Kotlin** and **Jetpack Compose**. It follows the **MVVM** architecture pattern. The app manages personal finances, tracking expenses, incomes, and transfers between accounts. It features voice recognition for quick data entry and supports multiple currencies.

## Architecture & Core Components

### UI Layer (Compose)
- **Single Activity:** `MainActivity.kt` is the entry point.
- **Navigation:** `NavGraph.kt` manages all navigation using `androidx.navigation.compose`. Routes are string-based (e.g., `"editExpense/{expenseId}?..."`).
- **Screens:** Located in `ui/screens/`. All screens consume `ExpenseViewModel`.
- **Theme:** Material3 design system.

### Data Layer (Room)
- **Database:** `AppDatabase` (Room) is the single source of truth.
- **Entities:**
  - `Expense`: Represents both expenses and incomes. Distinguished by `type` ("Expense" or "Income").
  - `Account`: Financial accounts (Cash, Card, etc.) with a balance and currency.
  - `Category`: Classification for expenses.
  - `TransferHistory`: Records of transfers between accounts.
- **Repositories:** Intermediaries between DAOs and ViewModel (e.g., `ExpenseRepository`).

### State Management
- **ViewModel:** `ExpenseViewModel` is the central state holder.
  - Uses `StateFlow` for exposing data lists (`allExpenses`, `allAccounts`, etc.) to the UI.
  - Uses `Channel` -> `Flow` for one-off events like navigation (`navigateToFlow`) and errors (`errorFlow`).
  - **Critical Logic:** Handles the business logic for "Transfers" and Voice Recognition parsing.

## Key Patterns & Conventions

### 1. Transfer Logic (Double-Entry Simulation)
Transfers are not just simple records. When a transfer is created:
1. A `TransferHistory` record is saved.
2. **Two** `Expense` records are automatically created:
   - One "Expense" type record for the **Source Account**.
   - One "Income" type record for the **Destination Account**.
   - Both have the category set to `"Transfer"`.
   - Both are linked via `transferId`.
**Rule:** Always use `ExpenseViewModel.insertTransfer` or `updateTransfer` to ensure these child expenses are kept in sync. Do not manually manipulate "Transfer" category expenses.

### 2. Voice Recognition
- The app parses natural language commands for adding transactions.
- Logic resides in `ExpenseViewModel` (`parseExpense`, `parseTransfer`).
- **Format:**
  - Expense: "Expense from [Account] [Amount] Category [Category]"
  - Transfer: "Transfer from [Source] to [Dest] [Amount] Comment [Comment]"

### 3. Account Management (Active vs. Archived)
- Accounts have an `active` boolean flag.
- `allActiveAccounts` flow filters for currently usable accounts.
- `archiveAccountWithRelations` performs a "soft delete" on accounts and their related transactions.

### 4. Navigation Arguments
- Arguments are passed as URL parameters in routes.
- Complex objects are not passed; pass IDs and fetch data in the ViewModel or pass primitive flags.
- Example: `"editExpense/{expenseId}?accountName={accountName}&..."`

## Developer Workflows

### Dependencies
- **Build System:** Gradle (Kotlin DSL).
- **Key Libs:** Jetpack Compose, Navigation Compose, Room, Coroutines/Flow, Google Sign-In, Google Sheets API.

### Common Tasks
- **Adding a Screen:**
  1. Create Composable in `ui/screens/`.
  2. Add route to `NavGraph.kt`.
  3. Handle navigation events in `ExpenseViewModel` if triggered by logic (e.g., voice command success).
- **Database Changes:**
  1. Modify Entity.
  2. Update DAO.
  3. Update `AppDatabase` version and provide migration (or fallback to destructive migration during dev).

## Code Style
- Use `BigDecimal` for all monetary values.
- Prefer `StateFlow` over `LiveData`.
- Use `viewModelScope` for coroutines.
- Keep UI logic out of Composables; delegate to ViewModel.
