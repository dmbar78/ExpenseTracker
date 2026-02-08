package com.example.expensetracker.data

import java.math.BigDecimal

/**
 * Root data class representing the complete backup file structure.
 * Matches the JSON schema defined in plan-backupRestoreViaJSON.prompt.md
 */
data class BackupData(
    val metadata: BackupMetadata,
    val data: BackupPayload,
    val integrityCheck: IntegrityCheck
)

/**
 * Metadata about the backup file.
 * Used for versioning and validation during restore.
 */
data class BackupMetadata(
    /** Version of the app that created the backup */
    val appVersion: String,
    /** Schema version for backward compatibility checks */
    val schemaVersion: Int,
    /** Unix timestamp when backup was created */
    val timestamp: Long,
    /** Device type that created the backup */
    val device: String
)

/**
 * Container for all backed up data entities.
 */
data class BackupPayload(
    val accounts: List<Account>,
    val categories: List<Category>,
    val keywords: List<Keyword>,
    val expenses: List<Expense>,
    val transferHistories: List<TransferHistory>,
    val currencies: List<Currency>,
    val exchangeRates: List<ExchangeRate>,
    val expenseKeywordCrossRefs: List<ExpenseKeywordCrossRef>,
    val debts: List<Debt>,
    val userPreferences: BackupUserPreferences?
)

/**
 * User preferences snapshot for backup.
 * Separate from the DataStore-based UserPreferences class.
 */
data class BackupUserPreferences(
    val defaultCurrencyCode: String
)

/**
 * Integrity verification data for the backup.
 */
data class IntegrityCheck(
    /** Total count of all records in the backup */
    val recordCount: Int,
    /** Optional SHA-256 checksum for verification */
    val checksum: String?
)
