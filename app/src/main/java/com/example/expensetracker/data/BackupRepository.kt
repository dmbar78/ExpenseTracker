package com.example.expensetracker.data

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
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
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupRepository(
    private val database: AppDatabase,
    private val userPreferences: UserPreferences,
    private val filesDir: File // Inject filesDir to access internal storage
) {
    enum class BackupFormat {
        PLAIN,
        ENCRYPTED,
        UNKNOWN
    }

    companion object {
        /** Current schema version for backups */
        const val CURRENT_SCHEMA_VERSION = 3
        
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
        val debts = database.debtDao().getAllDebtsOnce()

        // Get current default currency from UserPreferences
        val defaultCurrency = userPreferences.defaultCurrencyCode.first()

        // Process images for expenses
        val expenseImages = mutableMapOf<Int, String>()
        expenses.forEach { expense ->
            expense.photoUri?.let { uriString ->
                if (uriString.startsWith("file://")) {
                    try {
                        val file = File(Uri.parse(uriString).path!!)
                        if (file.exists()) {
                            val bytes = FileInputStream(file).use { it.readBytes() }
                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            expenseImages[expense.id] = base64
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val payload = BackupPayload(
            accounts = accounts,
            categories = categories,
            keywords = keywords,
            expenses = expenses,
            transferHistories = transferHistories,
            currencies = currencies,
            exchangeRates = exchangeRates,
            expenseKeywordCrossRefs = expenseKeywordCrossRefs,
            debts = debts,
            userPreferences = BackupUserPreferences(defaultCurrencyCode = defaultCurrency),
            expenseImages = expenseImages
        )

        val totalRecords = accounts.size + categories.size + keywords.size + 
                          expenses.size + transferHistories.size + 
                          currencies.size + exchangeRates.size + 
                          expenseKeywordCrossRefs.size + debts.size +
                          expenseImages.size


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
                database.debtDao().deleteAll() // Delete debts before expenses due to FK
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
                
                // Restore images and update expense URIs
                val restoredExpenses = backupData.data.expenses.map { expense ->
                    val base64Image = backupData.data.expenseImages?.get(expense.id)
                    if (base64Image != null) {
                        try {
                            val imagesDir = File(filesDir, "expense_images")
                            if (!imagesDir.exists()) imagesDir.mkdirs()
                            
                            val fileName = "img_${System.currentTimeMillis()}_${expense.id}.jpg"
                            val file = File(imagesDir, fileName)
                            
                            val bytes = Base64.decode(base64Image, Base64.NO_WRAP)
                            FileOutputStream(file).use { it.write(bytes) }
                            
                            expense.copy(photoUri = Uri.fromFile(file).toString())
                        } catch (e: Exception) {
                            e.printStackTrace()
                            expense.copy(photoUri = null) // Failed to restore image
                        }
                    } else {
                        expense.copy(photoUri = null) // No image in backup or wasn't backed up
                    }
                }
                
                database.expenseDao().insertAll(restoredExpenses)
                database.debtDao().insertAll(backupData.data.debts) // Expenses must exist first
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
     * Serialize BackupData directly to a stream to avoid building one large String.
     */
    fun writeJsonToStream(backupData: BackupData, outputStream: OutputStream) {
        outputStream.writer(Charsets.UTF_8).use { writer ->
            gson.toJson(backupData, writer)
            writer.flush()
        }
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
     * Serialize and encrypt BackupData directly to a stream to avoid large intermediate Strings.
     */
    fun writeEncryptedJsonToStream(backupData: BackupData, password: String, outputStream: OutputStream) {
        SecurityManager.encryptDataStream(password, outputStream) { plainTextOutput ->
            plainTextOutput.writer(Charsets.UTF_8).use { writer ->
                gson.toJson(backupData, writer)
                writer.flush()
            }
        }
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
     * Deserialize backup JSON from a reader to avoid loading full files into memory.
     */
    fun deserializeFromJson(reader: Reader): BackupData? {
        return try {
            gson.fromJson(reader, BackupData::class.java)
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

    /**
     * Deserialize encrypted backup JSON from a reader.
     */
    fun deserializeFromEncryptedJson(reader: Reader, password: String): BackupData? {
        return try {
            val payload = gson.fromJson(reader, SecurityManager.EncryptedBackupPayload::class.java)
                ?: return null
            val encryptedJson = Gson().toJson(payload)
            val decryptedJson = SecurityManager.decryptData(encryptedJson, password)
            gson.fromJson(decryptedJson, BackupData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deserialize encrypted backup from an input stream.
     * Supports stream-encrypted format and legacy JSON encrypted payloads.
     */
    fun deserializeFromEncryptedJson(inputStream: InputStream, password: String): BackupData? {
        return try {
            if (SecurityManager.isStreamingEncryptedBackup(inputStream)) {
                SecurityManager.decryptDataStream(inputStream, password).use { decryptedReader ->
                    gson.fromJson(decryptedReader, BackupData::class.java)
                }
            } else {
                inputStream.reader().use { reader ->
                    deserializeFromEncryptedJson(reader, password)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun isBackupEncrypted(json: String): Boolean {
        return SecurityManager.isEncrypted(json)
    }

    /**
     * Read only top-level keys to detect backup format without loading full content.
     */
    fun detectBackupFormat(reader: Reader): BackupFormat {
        return try {
            JsonReader(reader).use { jsonReader ->
                jsonReader.beginObject()
                var sawSalt = false
                var sawIv = false
                var fieldsRead = 0

                while (jsonReader.hasNext() && fieldsRead < 8) {
                    val name = jsonReader.nextName()
                    fieldsRead++
                    when (name) {
                        "metadata" -> return BackupFormat.PLAIN
                        "salt" -> {
                            sawSalt = true
                            jsonReader.skipValue()
                        }
                        "iv" -> {
                            sawIv = true
                            jsonReader.skipValue()
                        }
                        else -> jsonReader.skipValue()
                    }

                    if (sawSalt && sawIv) {
                        return BackupFormat.ENCRYPTED
                    }
                }
            }
            BackupFormat.UNKNOWN
        } catch (e: Exception) {
            BackupFormat.UNKNOWN
        }
    }

    /**
     * Detect backup format from stream, including stream-encrypted envelopes.
     */
    fun detectBackupFormat(inputStream: InputStream): BackupFormat {
        return if (SecurityManager.isStreamingEncryptedBackup(inputStream)) {
            BackupFormat.ENCRYPTED
        } else {
            inputStream.reader().use { reader ->
                detectBackupFormat(reader)
            }
        }
    }

    /**
     * Clear all data from the database.
     * Used internally during restore operations.
     */
    suspend fun clearAllData() {
        database.withTransaction {
            database.keywordDao().deleteAllCrossRefs()
            database.debtDao().deleteAll() // Delete debts before expenses
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

        // Validate debts reference valid expenses
        backupData.data.debts.forEach { debt ->
            if (debt.parentExpenseId !in expenseIds) {
                 return Result.failure(
                    IllegalArgumentException(
                        "Invalid backup: Debt ID ${debt.id} references non-existent parent expense ID ${debt.parentExpenseId}"
                    )
                )
            }
        }


        return Result.success(Unit)
    }
}
