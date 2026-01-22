# Backup & Restore Feature Plan

## Goal
Implement a robust, user-friendly backup and restore system to ensure data safety and portability. This includes a full-fidelity proprietary JSON format for app data backups and restoration.

## Primary Backup Strategy: Versioned JSON (App-Level)
**Why:** Preserves full fidelity (relationships, currencies, exact values), stable across schema changes, and allows for strict validation.

### Backup File Format
- **Filename:** `expense-tracker-backup-v{N}_YYYYMMDD_HHmmss.json`
- **Structure:**
  ```json
  {
    "metadata": {
      "appVersion": "1.0.0",
      "schemaVersion": 1,
      "timestamp": "2024-01-21T10:00:00Z",
      "device": "Android"
    },
    "data": {
      "accounts": [ ... ],
      "categories": [ ... ],
      "keywords": [ ... ],
      "expenses": [ ... ],
      "transferHistories": [ ... ],
      "currencies": [ ... ],
      "exchangeRates": [ ... ],
      "userPreferences": { ... } // Optional: Currency prefs, etc.
    },
    "integrityCheck": {
      "recordCount": 150,
      "checksum": "sha256-hash-optional"
    }
  }
  ```

### Key Rules
1.  **Atomicity:** The restore process must be transactional. If any part fails (e.g., corrupt JSON, foreign key violation), the database should roll back to its previous state.
2.  **Validation:**
    -   Check `schemaVersion`.
    -   Validate required fields (e.g., Account ID, Currency).
    -   Ensure referential integrity (e.g., Expense/TransferHistory links to an existing Account, Category, Keyword, ExchangeRate).
3.  **Idempotency Strategy:** **"Replace All"**.
    -   Warning: "This will overwrite all current data."
    -   Flow: `Delete All Tables` -> `Insert All from Backup`.
    -   *Reasoning:* Merging is complex (ID conflicts) and error-prone for personal finance apps where exact balances matter.

### Implementation Steps
1.  **Data Object:** Create a `BackupData` data class representing the JSON structure.
2.  **Serialization:** Use **Kotlinx Serialization** or **Gson** for converting `BackupData` <-> JSON.
3.  **DAOs:** Add `getAll()` methods for export and `insertAll()` / `deleteAll()` methods for import to all DAO interfaces (`ExpenseDao`, `AccountDao`, etc.).
4.  **Repository:** Create `BackupRepository` to handle the orchestration (reading from all DAOs, assembling the object).
5.  **ViewModel:** `BackupViewModel` to handle UI states (Loading, Success, Error).

## Storage & Security
-   **Storage Access Framework (SAF):**
    -   **Export:** Use `ActivityResultContracts.CreateDocument` to let the user save the file to Drive, Downloads, etc.
    -   **Import:** Use `ActivityResultContracts.GetContent` (mime-type `application/json`) to pick a file.
-   **Security:**
    -   *Phase 1:* No encryption. Rely on user's secure storage (Google Drive, private local folder).
    -   *Phase 2 (Future):* Encryption with user-provided password using AES-GCM (Cipher input/output streams).

## 4. UI/UX
-   **Settings Screen:** Add "Data Management" section.
-   **Export:**
    -   "Export Backup (JSON)" -> Saves full backup.
    **Import:**
    -   "Restore from Backup" -> File Picker -> Confirmation Dialog ("Warning: Overwrite?") -> Progress Bar -> Success/Error.

## Developer Notes
-   **Entities to Include:**
    -   `Account`
    -   `Expense`
        `TransferHistory`
    -   `Category`
    -   `Keyword`
        `Currency`
        `ExchangeRate`
        `UserPreferences`
        `
-   **Testing:** 
    - Create a sample JSON with known "bad" data to test validation logic.
    - Check metadata versioning and backward compatibility.
    - Test restore with empty database.
    - Test restore with existing data (should overwrite).
    - Test restore with corrupted JSON (should fail gracefully).
    - Test UI Export: open settings -> click export -> select location -> verify file created.
    - Test UI Import: open settings -> click import -> select file -> confirm overwrite -> verify data restored.
