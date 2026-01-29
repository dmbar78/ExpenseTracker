package com.example.expensetracker.data

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.first

/**
 * Repository for backup and restore operations.
 * Handles exporting database to BackupData and restoring from BackupData.
 * 
 * Key features:
 * - Transactional restore (all-or-nothing)
 * - Schema version validation
 * - Data validation before restore
 */
class BackupRepository(
    private val database: AppDatabase,
    private val userPreferences: UserPreferences
) {
    companion object {
        /** Current schema version for backups */
        const val CURRENT_SCHEMA_VERSION = 2
        
        /** App version - should be read from BuildConfig in production */
        const val APP_VERSION = "1.0.0"
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    /**
     * Export all database content to a BackupData object.
     * 
     * @return BackupData containing all entities and metadata
     */
    suspend fun exportBackupData(): BackupData {
        val accounts = database.accountDao().getAllAccountsOnce()
        val categories = database.categoryDao().getAllCategoriesOnce()
        val keywords = database.keywordDao().getAllKeywordsOnce()
        val expenses = database.expenseDao().getAllExpensesOnce()
        val transferHistories = database.transferHistoryDao().getAllTransfersOnce()
        val currencies = database.currencyDao().getAllCurrenciesOnce()
        val exchangeRates = database.exchangeRateDao().getAllRatesOnce()
        val expenseKeywordCrossRefs = database.keywordDao().getAllCrossRefsOnce()

        // Get current default currency from UserPreferences
        val defaultCurrency = userPreferences.defaultCurrencyCode.first()

        val payload = BackupPayload(
            accounts = accounts,
            categories = categories,
            keywords = keywords,
            expenses = expenses,
            transferHistories = transferHistories,
            currencies = currencies,
            exchangeRates = exchangeRates,
            expenseKeywordCrossRefs = expenseKeywordCrossRefs,
            userPreferences = BackupUserPreferences(defaultCurrencyCode = defaultCurrency)
        )

        val totalRecords = accounts.size + categories.size + keywords.size + 
                          expenses.size + transferHistories.size + 
                          currencies.size + exchangeRates.size + 
                          expenseKeywordCrossRefs.size

        return BackupData(
            metadata = BackupMetadata(
                appVersion = APP_VERSION,
                schemaVersion = CURRENT_SCHEMA_VERSION,
                timestamp = System.currentTimeMillis(),
                device = "Android"
            ),
            data = payload,
            integrityCheck = IntegrityCheck(
                recordCount = totalRecords,
                checksum = null // Could add SHA-256 of JSON in future
            )
        )
    }

    /**
     * Restore database from backup data.
     * This will REPLACE ALL existing data (destructive operation).
     * 
     * The restore is transactional - if any step fails, the entire operation
     * is rolled back and existing data is preserved.
     * 
     * @param backupData The backup data to restore
     * @return Result indicating success or failure with error details
     */
    suspend fun restoreBackupData(backupData: BackupData): Result<Unit> {
        // Validate schema version
        val validationResult = validateBackupData(backupData)
        if (validationResult.isFailure) {
            return validationResult
        }

        return try {
            database.withTransaction {
                // Clear all existing data (order matters due to foreign keys)
                database.keywordDao().deleteAllCrossRefs()
                database.expenseDao().deleteAll()
                database.transferHistoryDao().deleteAll()
                database.exchangeRateDao().deleteAll()
                database.keywordDao().deleteAllKeywords()
                database.categoryDao().deleteAll()
                database.currencyDao().deleteAll()
                database.accountDao().deleteAll()

                // Insert all data from backup (order matters due to foreign keys)
                database.accountDao().insertAll(backupData.data.accounts)
                database.categoryDao().insertAll(backupData.data.categories)
                database.currencyDao().insertAll(backupData.data.currencies)
                database.keywordDao().insertAllKeywords(backupData.data.keywords)
                database.exchangeRateDao().insertAll(backupData.data.exchangeRates)
                database.expenseDao().insertAll(backupData.data.expenses)
                database.transferHistoryDao().insertAll(backupData.data.transferHistories)
                database.keywordDao().insertAllCrossRefs(backupData.data.expenseKeywordCrossRefs)
            }
            
            // Restore user preferences (outside transaction as it's DataStore, not Room)
            backupData.data.userPreferences?.let { prefs ->
                userPreferences.setDefaultCurrencyCode(prefs.defaultCurrencyCode)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Serialize BackupData to JSON string.
     * 
     * @param backupData The data to serialize
     * @return JSON string representation
     */
    fun serializeToJson(backupData: BackupData): String {
        return gson.toJson(backupData)
    }

    /**
     * Serialize BackupData to Encrypted JSON string.
     *
     * @param backupData The data to serialize
     * @param password The password to encrypt with
     * @return Encrypted JSON string
     */
    fun serializeToEncryptedJson(backupData: BackupData, password: String): String {
        val json = gson.toJson(backupData)
        return SecurityManager.encryptData(json, password)
    }

    /**
     * Deserialize JSON string to BackupData.
     * 
     * @param json The JSON string to parse
     * @return BackupData or null if parsing fails
     */
    fun deserializeFromJson(json: String): BackupData? {
        if (json.isBlank()) {
            return null
        }
        return try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deserialize Encrypted JSON string to BackupData.
     *
     * @param json The encrypted JSON string
     * @param password The password to decrypt with
     * @return BackupData or null if decryption/parsing fails
     */
    fun deserializeFromEncryptedJson(json: String, password: String): BackupData? {
        if (json.isBlank()) return null
        return try {
            val decryptedJson = SecurityManager.decryptData(json, password)
            gson.fromJson(decryptedJson, BackupData::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun isBackupEncrypted(json: String): Boolean {
        return SecurityManager.isEncrypted(json)
    }

    /**
     * Clear all data from the database.
     * Used internally during restore operations.
     */
    suspend fun clearAllData() {
        database.withTransaction {
            database.keywordDao().deleteAllCrossRefs()
            database.expenseDao().deleteAll()
            database.transferHistoryDao().deleteAll()
            database.exchangeRateDao().deleteAll()
            database.keywordDao().deleteAllKeywords()
            database.categoryDao().deleteAll()
            database.currencyDao().deleteAll()
            database.accountDao().deleteAll()
        }
    }

    /**
     * Validate backup data before restore.
     * Checks schema version, required fields, and referential integrity.
     * 
     * @param backupData The data to validate
     * @return Result with validation errors if any
     */
    private fun validateBackupData(backupData: BackupData): Result<Unit> {
        // Check schema version compatibility
        if (backupData.metadata.schemaVersion > CURRENT_SCHEMA_VERSION) {
            return Result.failure(
                IllegalArgumentException(
                    "Unsupported schema version ${backupData.metadata.schemaVersion}. " +
                    "This app supports schema version $CURRENT_SCHEMA_VERSION or lower. " +
                    "Please update the app to restore this backup."
                )
            )
        }

        // Validate accounts have required fields
        backupData.data.accounts.forEach { account ->
            if (account.name.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Invalid backup: Account with ID ${account.id} has empty name")
                )
            }
            if (account.currency.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Invalid backup: Account '${account.name}' has empty currency")
                )
            }
        }

        // Validate categories have required fields
        backupData.data.categories.forEach { category ->
            if (category.name.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Invalid backup: Category with ID ${category.id} has empty name")
                )
            }
        }

        // Validate expenses reference valid accounts
        val accountNames = backupData.data.accounts.map { it.name.lowercase() }.toSet()
        backupData.data.expenses.forEach { expense ->
            if (expense.account.lowercase() !in accountNames) {
                return Result.failure(
                    IllegalArgumentException(
                        "Invalid backup: Expense ID ${expense.id} references non-existent account '${expense.account}'"
                    )
                )
            }
        }

        // Validate transfer histories reference valid accounts
        backupData.data.transferHistories.forEach { transfer ->
            if (transfer.sourceAccount.lowercase() !in accountNames) {
                return Result.failure(
                    IllegalArgumentException(
                        "Invalid backup: Transfer ID ${transfer.id} references non-existent source account '${transfer.sourceAccount}'"
                    )
                )
            }
            if (transfer.destinationAccount.lowercase() !in accountNames) {
                return Result.failure(
                    IllegalArgumentException(
                        "Invalid backup: Transfer ID ${transfer.id} references non-existent destination account '${transfer.destinationAccount}'"
                    )
                )
            }
        }

        // Validate expense-keyword cross refs
        val expenseIds = backupData.data.expenses.map { it.id }.toSet()
        val keywordIds = backupData.data.keywords.map { it.id }.toSet()
        backupData.data.expenseKeywordCrossRefs.forEach { crossRef ->
            if (crossRef.expenseId !in expenseIds) {
                return Result.failure(
                    IllegalArgumentException(
                        "Invalid backup: Cross-ref references non-existent expense ID ${crossRef.expenseId}"
                    )
                )
            }
            if (crossRef.keywordId !in keywordIds) {
                return Result.failure(
                    IllegalArgumentException(
                        "Invalid backup: Cross-ref references non-existent keyword ID ${crossRef.keywordId}"
                    )
                )
            }
        }

        return Result.success(Unit)
    }
}
