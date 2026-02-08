package com.example.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Expense::class, Account::class, Category::class, Currency::class, Keyword::class, TransferHistory::class, ExchangeRate::class, ExpenseKeywordCrossRef::class, Debt::class], version = 17, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun keywordDao(): KeywordDao
    abstract fun transferHistoryDao(): TransferHistoryDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun debtDao(): DebtDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from version 11 to 12: Convert Double (REAL) columns to BigDecimal (TEXT).
         * Uses create-copy-rename approach since SQLite cannot ALTER COLUMN type.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migrate accounts table: balance REAL -> TEXT
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS accounts_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL COLLATE NOCASE,
                        currency TEXT NOT NULL,
                        balance TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO accounts_new (id, name, currency, balance)
                    SELECT id, name, currency, printf('%.2f', balance)
                    FROM accounts
                """.trimIndent())
                database.execSQL("DROP TABLE accounts")
                database.execSQL("ALTER TABLE accounts_new RENAME TO accounts")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_name ON accounts (name)")

                // Migrate expenses table: amount REAL -> TEXT
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS expenses_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        account TEXT NOT NULL,
                        amount TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        category TEXT NOT NULL,
                        expenseDate INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        comment TEXT
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO expenses_new (id, account, amount, currency, category, expenseDate, type, comment)
                    SELECT id, account, printf('%.2f', amount), currency, category, expenseDate, type, comment
                    FROM expenses
                """.trimIndent())
                database.execSQL("DROP TABLE expenses")
                database.execSQL("ALTER TABLE expenses_new RENAME TO expenses")

                // Migrate transfer_history table: amount REAL -> TEXT
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS transfer_history_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date INTEGER NOT NULL,
                        sourceAccount TEXT NOT NULL,
                        destinationAccount TEXT NOT NULL,
                        amount TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        comment TEXT
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO transfer_history_new (id, date, sourceAccount, destinationAccount, amount, currency, comment)
                    SELECT id, date, sourceAccount, destinationAccount, printf('%.2f', amount), currency, comment
                    FROM transfer_history
                """.trimIndent())
                database.execSQL("DROP TABLE transfer_history")
                database.execSQL("ALTER TABLE transfer_history_new RENAME TO transfer_history")
            }
        }

        /**
         * Migration from version 12 to 13: Add exchange rates table and snapshot fields to expenses/transfers.
         * - Creates exchange_rates table for currency pair rates by date
         * - Adds originalDefaultCurrencyCode, exchangeRateToOriginalDefault, amountInOriginalDefault to expenses
         * - Adds originalDefaultCurrencyCode, exchangeRateToOriginalDefault, amountInOriginalDefault to transfer_history
         * Legacy rows will have NULL values for new columns (to be filled by reconciliation).
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create exchange_rates table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS exchange_rates (
                        date INTEGER NOT NULL,
                        baseCurrencyCode TEXT NOT NULL,
                        quoteCurrencyCode TEXT NOT NULL,
                        rate TEXT NOT NULL,
                        PRIMARY KEY (date, baseCurrencyCode, quoteCurrencyCode)
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_exchange_rates_baseCurrencyCode_quoteCurrencyCode_date 
                    ON exchange_rates (baseCurrencyCode, quoteCurrencyCode, date)
                """.trimIndent())
                
                // Add new columns to expenses table
                database.execSQL("ALTER TABLE expenses ADD COLUMN originalDefaultCurrencyCode TEXT")
                database.execSQL("ALTER TABLE expenses ADD COLUMN exchangeRateToOriginalDefault TEXT")
                database.execSQL("ALTER TABLE expenses ADD COLUMN amountInOriginalDefault TEXT")
                
                // Add new columns to transfer_history table
                database.execSQL("ALTER TABLE transfer_history ADD COLUMN originalDefaultCurrencyCode TEXT")
                database.execSQL("ALTER TABLE transfer_history ADD COLUMN exchangeRateToOriginalDefault TEXT")
                database.execSQL("ALTER TABLE transfer_history ADD COLUMN amountInOriginalDefault TEXT")
            }
        }

        /**
         * Migration from version 13 to 14: Add expense-keyword join table and update keywords table.
         * - Creates expense_keyword_cross_ref table for many-to-many expense-keyword relationships
         * - Recreates keywords table with unique index and NOCASE collation on name
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create expense_keyword_cross_ref table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS expense_keyword_cross_ref (
                        expenseId INTEGER NOT NULL,
                        keywordId INTEGER NOT NULL,
                        PRIMARY KEY (expenseId, keywordId),
                        FOREIGN KEY (expenseId) REFERENCES expenses(id) ON DELETE CASCADE,
                        FOREIGN KEY (keywordId) REFERENCES keywords(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_expense_keyword_cross_ref_expenseId ON expense_keyword_cross_ref (expenseId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_expense_keyword_cross_ref_keywordId ON expense_keyword_cross_ref (keywordId)")

                // Recreate keywords table with unique index and NOCASE collation
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS keywords_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL COLLATE NOCASE
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT OR IGNORE INTO keywords_new (id, name)
                    SELECT id, name FROM keywords
                """.trimIndent())
                database.execSQL("DROP TABLE keywords")
                database.execSQL("ALTER TABLE keywords_new RENAME TO keywords")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_keywords_name ON keywords (name)")
            }
        }
        
         /**
         * Migration from version 14 to 15: Add destinationAmount and destinationCurrency to transfer_history.
         * - Adds nullable columns for separate destination amount/currency (multi-currency support).
         * - Backfills existing rows: destinationCurrency = source currency, destinationAmount = NULL.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transfer_history ADD COLUMN destinationAmount TEXT")
                database.execSQL("ALTER TABLE transfer_history ADD COLUMN destinationCurrency TEXT")
                
                // Set default destination currency to match source currency for existing records
                database.execSQL("UPDATE transfer_history SET destinationCurrency = currency")
                
                // destinationAmount defaults to NULL (which implies same as source amount logic in Repo)
                // Explicitly setting it to NULL just to be safe, though existing columns default to NULL usually.
                database.execSQL("UPDATE transfer_history SET destinationAmount = NULL")
            }
        }

        /**
         * Migration from version 15 to 16: Add Debt functionality.
         * - Creates debts table
         * - Adds relatedDebtId column to expenses table
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create debts table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS debts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        parentExpenseId INTEGER NOT NULL,
                        notes TEXT,
                        status TEXT NOT NULL,
                        FOREIGN KEY (parentExpenseId) REFERENCES expenses(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Add relatedDebtId to expenses
                database.execSQL("ALTER TABLE expenses ADD COLUMN relatedDebtId INTEGER")
            }
        }

        /**
         * Migration from version 16 to 17: Add index for parentExpenseId in debts table.
         * - Performance optimization and KSP warning fix.
         */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add index for parentExpenseId
                database.execSQL("CREATE INDEX IF NOT EXISTS index_debts_parentExpenseId ON debts (parentExpenseId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}