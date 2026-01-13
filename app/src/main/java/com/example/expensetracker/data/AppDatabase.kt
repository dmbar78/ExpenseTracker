package com.example.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Expense::class, Account::class, Category::class, Currency::class, Keyword::class, TransferHistory::class, ExchangeRate::class], version = 13, exportSchema = false)
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13)                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}