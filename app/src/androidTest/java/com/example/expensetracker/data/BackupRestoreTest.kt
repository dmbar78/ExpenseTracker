package com.example.expensetracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Instrumented tests for Backup/Restore functionality.
 * Tests the BackupRepository for creating and restoring JSON backups.
 * 
 * Based on plan-backupRestoreViaJSON.prompt.md:
 * - Test restore with empty database
 * - Test restore with existing data (should overwrite)
 * - Test restore with corrupted JSON (should fail gracefully)
 * - Test metadata versioning and backward compatibility
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private lateinit var database: AppDatabase
    private lateinit var backupRepository: BackupRepository
    private lateinit var userPreferences: UserPreferences

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        userPreferences = UserPreferences(context)
        backupRepository = BackupRepository(database, userPreferences)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Export Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun export_emptyDatabase_returnsValidBackupData() = runBlocking {
        // Given: an empty database
        
        // When: we export backup data
        val backupData = backupRepository.exportBackupData()
        
        // Then: backup data should be valid with empty lists
        assertNotNull(backupData)
        assertNotNull(backupData.metadata)
        assertEquals(1, backupData.metadata.schemaVersion)
        assertNotNull(backupData.data)
        assertTrue(backupData.data.accounts.isEmpty())
        assertTrue(backupData.data.expenses.isEmpty())
        assertTrue(backupData.data.categories.isEmpty())
        assertTrue(backupData.data.keywords.isEmpty())
        assertTrue(backupData.data.transferHistories.isEmpty())
        assertTrue(backupData.data.currencies.isEmpty())
        assertTrue(backupData.data.exchangeRates.isEmpty())
    }

    @Test
    fun export_withData_includesAllEntities() = runBlocking {
        // Given: database with various entities
        val account = Account(name = "TestAccount", currency = "EUR", balance = BigDecimal("100.00"))
        database.accountDao().insert(account)
        
        val category = Category(name = "TestCategory")
        database.categoryDao().insert(category)
        
        val keyword = Keyword(name = "TestKeyword")
        database.keywordDao().insert(keyword)
        
        val currency = Currency(code = "USD", name = "United States Dollar")
        database.currencyDao().insert(currency)
        
        // When: we export backup data
        val backupData = backupRepository.exportBackupData()
        
        // Then: all entities should be included
        assertEquals(1, backupData.data.accounts.size)
        assertEquals("TestAccount", backupData.data.accounts[0].name)
        
        assertEquals(1, backupData.data.categories.size)
        assertEquals("TestCategory", backupData.data.categories[0].name)
        
        assertEquals(1, backupData.data.keywords.size)
        assertEquals("TestKeyword", backupData.data.keywords[0].name)
        
        assertEquals(1, backupData.data.currencies.size)
        assertEquals("USD", backupData.data.currencies[0].code)
    }

    @Test
    fun export_containsMetadataWithAppVersionAndTimestamp() = runBlocking {
        // Given: any database state
        
        // When: we export backup data
        val backupData = backupRepository.exportBackupData()
        
        // Then: metadata should contain required fields
        assertNotNull(backupData.metadata.appVersion)
        assertTrue(backupData.metadata.appVersion.isNotEmpty())
        assertTrue(backupData.metadata.schemaVersion > 0)
        assertTrue(backupData.metadata.timestamp > 0)
        assertEquals("Android", backupData.metadata.device)
    }

    @Test
    fun export_containsIntegrityCheck() = runBlocking {
        // Given: database with some records
        database.accountDao().insert(Account(name = "A1", currency = "EUR", balance = BigDecimal.ZERO))
        database.categoryDao().insert(Category(name = "C1"))
        
        // When: we export backup data  
        val backupData = backupRepository.exportBackupData()
        
        // Then: integrity check should reflect record count
        assertNotNull(backupData.integrityCheck)
        assertTrue(backupData.integrityCheck.recordCount >= 2)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Import/Restore Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun restore_toEmptyDatabase_insertsAllData() = runBlocking {
        // Given: valid backup data
        val backupData = createSampleBackupData()
        
        // When: we restore the backup
        val result = backupRepository.restoreBackupData(backupData)
        
        // Then: all data should be inserted
        assertTrue(result.isSuccess)
        assertEquals(1, database.accountDao().getAllAccounts().first().size)
        assertEquals(1, database.categoryDao().getAllCategories().first().size)
        assertEquals(1, database.keywordDao().getAllKeywords().first().size)
    }

    @Test
    fun restore_toPopulatedDatabase_overwritesExistingData() = runBlocking {
        // Given: database with existing data
        database.accountDao().insert(Account(name = "OldAccount", currency = "USD", balance = BigDecimal("50.00")))
        database.categoryDao().insert(Category(name = "OldCategory"))
        assertEquals(1, database.accountDao().getAllAccounts().first().size)
        
        // And: backup data with different content
        val backupData = createSampleBackupData()
        
        // When: we restore the backup
        val result = backupRepository.restoreBackupData(backupData)
        
        // Then: old data should be replaced with backup data
        assertTrue(result.isSuccess)
        val accounts = database.accountDao().getAllAccounts().first()
        assertEquals(1, accounts.size)
        assertEquals("BackupAccount", accounts[0].name)
        
        val categories = database.categoryDao().getAllCategories().first()
        assertEquals(1, categories.size)
        assertEquals("BackupCategory", categories[0].name)
    }

    @Test
    fun restore_withCorruptedData_failsGracefully() = runBlocking {
        // Given: database with existing data
        database.accountDao().insert(Account(name = "ExistingAccount", currency = "EUR", balance = BigDecimal("100.00")))
        
        // And: corrupted backup data (missing required fields)
        val corruptedBackupData = createCorruptedBackupData()
        
        // When: we attempt to restore
        val result = backupRepository.restoreBackupData(corruptedBackupData)
        
        // Then: restore should fail
        assertTrue(result.isFailure)
        
        // And: existing data should be intact (rollback)
        val accounts = database.accountDao().getAllAccounts().first()
        assertEquals(1, accounts.size)
        assertEquals("ExistingAccount", accounts[0].name)
    }

    @Test
    fun restore_withInvalidSchemaVersion_failsWithClearError() = runBlocking {
        // Given: backup data with unsupported schema version
        val futureSchemaBackup = createSampleBackupData().copy(
            metadata = BackupMetadata(
                appVersion = "1.0.0",
                schemaVersion = 999, // Future version
                timestamp = System.currentTimeMillis(),
                device = "Android"
            )
        )
        
        // When: we attempt to restore
        val result = backupRepository.restoreBackupData(futureSchemaBackup)
        
        // Then: should fail with schema version error
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("schema", ignoreCase = true) == true)
    }

    @Test
    fun restore_isTransactional_rollsBackOnFailure() = runBlocking {
        // Given: database with initial state
        val originalAccount = Account(name = "OriginalAccount", currency = "EUR", balance = BigDecimal("200.00"))
        database.accountDao().insert(originalAccount)
        
        // And: backup data that will fail midway (e.g., due to foreign key constraint)
        val badBackupData = createBackupDataWithInvalidReferences()
        
        // When: we attempt to restore (expecting failure)
        val result = backupRepository.restoreBackupData(badBackupData)
        
        // Then: should fail
        assertTrue(result.isFailure)
        
        // And: original data should still exist (transaction rolled back)
        val accounts = database.accountDao().getAllAccounts().first()
        assertEquals(1, accounts.size)
        assertEquals("OriginalAccount", accounts[0].name)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Serialization Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun serialize_andDeserialize_preservesData() = runBlocking {
        // Given: backup data with all entity types
        val originalBackup = createFullBackupData()
        
        // When: we serialize to JSON and deserialize back
        val json = backupRepository.serializeToJson(originalBackup)
        val deserialized = backupRepository.deserializeFromJson(json)
        
        // Then: data should be identical
        assertNotNull(deserialized)
        assertEquals(originalBackup.metadata.schemaVersion, deserialized!!.metadata.schemaVersion)
        assertEquals(originalBackup.data.accounts.size, deserialized.data.accounts.size)
        assertEquals(originalBackup.data.expenses.size, deserialized.data.expenses.size)
        assertEquals(originalBackup.data.categories.size, deserialized.data.categories.size)
        assertEquals(originalBackup.data.keywords.size, deserialized.data.keywords.size)
        assertEquals(originalBackup.data.transferHistories.size, deserialized.data.transferHistories.size)
        assertEquals(originalBackup.data.currencies.size, deserialized.data.currencies.size)
        assertEquals(originalBackup.data.exchangeRates.size, deserialized.data.exchangeRates.size)
    }

    @Test
    fun deserialize_invalidJson_returnsNull() = runBlocking {
        // Given: malformed JSON
        val invalidJson = "{ this is not valid json }"
        
        // When: we attempt to deserialize
        val result = backupRepository.deserializeFromJson(invalidJson)
        
        // Then: should return null (not throw)
        assertNull(result)
    }

    @Test
    fun deserialize_emptyString_returnsNull() = runBlocking {
        // When: we deserialize empty string
        val result = backupRepository.deserializeFromJson("")
        
        // Then: should return null
        assertNull(result)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Round-Trip Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun fullRoundTrip_exportThenRestore_preservesAllData() = runBlocking {
        // Given: database with comprehensive data
        setupDatabaseWithFullData()
        
        // When: we export
        val backupData = backupRepository.exportBackupData()
        val json = backupRepository.serializeToJson(backupData)
        
        // And: clear the database
        backupRepository.clearAllData()
        assertEquals(0, database.accountDao().getAllAccounts().first().size)
        
        // And: restore from the exported data
        val restoredData = backupRepository.deserializeFromJson(json)!!
        val result = backupRepository.restoreBackupData(restoredData)
        
        // Then: all original data should be restored
        assertTrue(result.isSuccess)
        assertTrue(database.accountDao().getAllAccounts().first().isNotEmpty())
        assertTrue(database.categoryDao().getAllCategories().first().isNotEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper Functions
    // ─────────────────────────────────────────────────────────────────────────────

    private fun createSampleBackupData(): BackupData {
        return BackupData(
            metadata = BackupMetadata(
                appVersion = "1.0.0",
                schemaVersion = 1,
                timestamp = System.currentTimeMillis(),
                device = "Android"
            ),
            data = BackupPayload(
                accounts = listOf(Account(id = 1, name = "BackupAccount", currency = "EUR", balance = BigDecimal("100.00"))),
                categories = listOf(Category(id = 1, name = "BackupCategory")),
                keywords = listOf(Keyword(id = 1, name = "BackupKeyword")),
                expenses = emptyList(),
                transferHistories = emptyList(),
                currencies = emptyList(),
                exchangeRates = emptyList(),
                expenseKeywordCrossRefs = emptyList(),
                userPreferences = null
            ),
            integrityCheck = IntegrityCheck(recordCount = 3, checksum = null)
        )
    }

    private fun createCorruptedBackupData(): BackupData {
        // Create backup data that will fail validation (e.g., account with empty name)
        return BackupData(
            metadata = BackupMetadata(
                appVersion = "1.0.0",
                schemaVersion = 1,
                timestamp = System.currentTimeMillis(),
                device = "Android"
            ),
            data = BackupPayload(
                accounts = listOf(Account(id = 1, name = "", currency = "", balance = BigDecimal("-1.00"))), // Invalid
                categories = emptyList(),
                keywords = emptyList(),
                expenses = emptyList(),
                transferHistories = emptyList(),
                currencies = emptyList(),
                exchangeRates = emptyList(),
                expenseKeywordCrossRefs = emptyList(),
                userPreferences = null
            ),
            integrityCheck = IntegrityCheck(recordCount = 0, checksum = null)
        )
    }

    private fun createBackupDataWithInvalidReferences(): BackupData {
        // Expense references non-existent account - should cause integrity failure
        return BackupData(
            metadata = BackupMetadata(
                appVersion = "1.0.0",
                schemaVersion = 1,
                timestamp = System.currentTimeMillis(),
                device = "Android"
            ),
            data = BackupPayload(
                accounts = emptyList(), // No accounts
                categories = listOf(Category(id = 1, name = "SomeCategory")),
                keywords = emptyList(),
                expenses = listOf(
                    Expense(
                        id = 1,
                        account = "NonExistentAccount", // References missing account
                        amount = BigDecimal("50.00"),
                        currency = "EUR",
                        category = "SomeCategory",
                        type = "Expense"
                    )
                ),
                transferHistories = emptyList(),
                currencies = emptyList(),
                exchangeRates = emptyList(),
                expenseKeywordCrossRefs = emptyList(),
                userPreferences = null
            ),
            integrityCheck = IntegrityCheck(recordCount = 2, checksum = null)
        )
    }

    private fun createFullBackupData(): BackupData {
        return BackupData(
            metadata = BackupMetadata(
                appVersion = "1.0.0",
                schemaVersion = 1,
                timestamp = System.currentTimeMillis(),
                device = "Android"
            ),
            data = BackupPayload(
                accounts = listOf(
                    Account(id = 1, name = "Cash", currency = "EUR", balance = BigDecimal("500.00")),
                    Account(id = 2, name = "Card", currency = "EUR", balance = BigDecimal("1000.00"))
                ),
                categories = listOf(
                    Category(id = 1, name = "Food"),
                    Category(id = 2, name = "Transport")
                ),
                keywords = listOf(
                    Keyword(id = 1, name = "restaurant"),
                    Keyword(id = 2, name = "taxi")
                ),
                expenses = listOf(
                    Expense(
                        id = 1,
                        account = "Cash",
                        amount = BigDecimal("20.00"),
                        currency = "EUR",
                        category = "Food",
                        type = "Expense"
                    )
                ),
                transferHistories = listOf(
                    TransferHistory(
                        id = 1,
                        sourceAccount = "Card",
                        destinationAccount = "Cash",
                        amount = BigDecimal("100.00"),
                        currency = "EUR"
                    )
                ),
                currencies = listOf(
                    Currency(id = 1, code = "EUR", name = "Euro"),
                    Currency(id = 2, code = "USD", name = "US Dollar")
                ),
                exchangeRates = listOf(
                    ExchangeRate(
                        date = System.currentTimeMillis(),
                        baseCurrencyCode = "EUR",
                        quoteCurrencyCode = "USD",
                        rate = BigDecimal("1.10")
                    )
                ),
                expenseKeywordCrossRefs = emptyList(),
                userPreferences = BackupUserPreferences(defaultCurrencyCode = "EUR")
            ),
            integrityCheck = IntegrityCheck(recordCount = 10, checksum = null)
        )
    }

    private suspend fun setupDatabaseWithFullData() {
        database.accountDao().insert(Account(name = "TestCash", currency = "EUR", balance = BigDecimal("100.00")))
        database.categoryDao().insert(Category(name = "TestFood"))
        database.keywordDao().insert(Keyword(name = "testkeyword"))
        database.currencyDao().insert(Currency(code = "EUR", name = "Euro"))
    }

    @Test
    fun export_includesDefaultCurrency() = runBlocking {
        // Set a custom default currency
        userPreferences.setDefaultCurrencyCode("USD")
        
        // Export backup
        val backupData = backupRepository.exportBackupData()
        
        // Verify userPreferences is included with correct currency
        assertNotNull(backupData.data.userPreferences)
        assertEquals("USD", backupData.data.userPreferences?.defaultCurrencyCode)
    }

    @Test
    fun restore_restoresDefaultCurrency() = runBlocking {
        // Set initial currency
        userPreferences.setDefaultCurrencyCode("EUR")
        assertEquals("EUR", userPreferences.defaultCurrencyCode.first())
        
        // Create backup with different currency
        val backupData = createSampleBackupData().copy(
            data = createSampleBackupData().data.copy(
                userPreferences = BackupUserPreferences(defaultCurrencyCode = "GBP")
            )
        )
        
        // Restore backup
        val result = backupRepository.restoreBackupData(backupData)
        
        // Verify restore succeeded and currency was updated
        assertTrue(result.isSuccess)
        assertEquals("GBP", userPreferences.defaultCurrencyCode.first())
    }

    @Test
    fun fullRoundTrip_preservesDefaultCurrency() = runBlocking {
        // Set up database with data
        setupDatabaseWithFullData()
        userPreferences.setDefaultCurrencyCode("CHF")
        
        // Export
        val exported = backupRepository.exportBackupData()
        assertEquals("CHF", exported.data.userPreferences?.defaultCurrencyCode)
        
        // Change currency
        userPreferences.setDefaultCurrencyCode("JPY")
        assertEquals("JPY", userPreferences.defaultCurrencyCode.first())
        
        // Restore
        val result = backupRepository.restoreBackupData(exported)
        assertTrue(result.isSuccess)
        
        // Verify currency was restored to original value
        assertEquals("CHF", userPreferences.defaultCurrencyCode.first())
    }
}
