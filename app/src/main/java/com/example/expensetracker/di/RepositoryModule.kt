package com.example.expensetracker.di

import android.content.Context
import com.example.expensetracker.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing repository dependencies.
 * 
 * Repositories are provided as singletons to ensure consistent
 * data access across the application.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideExpenseRepository(expenseDao: ExpenseDao): ExpenseRepository {
        return ExpenseRepository(expenseDao)
    }

    @Provides
    @Singleton
    fun provideAccountRepository(accountDao: AccountDao): AccountRepository {
        return AccountRepository(accountDao)
    }

    @Provides
    @Singleton
    fun provideCategoryRepository(categoryDao: CategoryDao): CategoryRepository {
        return CategoryRepository(categoryDao)
    }

    @Provides
    @Singleton
    fun provideCurrencyRepository(currencyDao: CurrencyDao): CurrencyRepository {
        return CurrencyRepository(currencyDao)
    }

    @Provides
    @Singleton
    fun provideTransferHistoryRepository(transferHistoryDao: TransferHistoryDao): TransferHistoryRepository {
        return TransferHistoryRepository(transferHistoryDao)
    }

    @Provides
    @Singleton
    fun provideLedgerRepository(ledgerDao: LedgerDao): LedgerRepository {
        return LedgerRepository(ledgerDao)
    }

    @Provides
    @Singleton
    fun provideDebtRepository(debtDao: DebtDao): DebtRepository {
        return DebtRepository(debtDao)
    }

    @Provides
    @Singleton
    fun provideRatesProvider(): RatesProvider {
        return CompositeRatesProvider(
            listOf(
                FawazAhmedRatesProvider(CdnUrlStrategy),
                FawazAhmedRatesProvider(PagesUrlStrategy),
                FrankfurterRatesProvider()
            )
        )
    }

    @Provides
    @Singleton
    fun provideExchangeRateRepository(
        exchangeRateDao: ExchangeRateDao,
        ratesProvider: RatesProvider
    ): ExchangeRateRepository {
        return ExchangeRateRepository(exchangeRateDao, ratesProvider)
    }

    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideBackupRepository(
        database: AppDatabase,
        userPreferences: UserPreferences
    ): BackupRepository {
        return BackupRepository(database, userPreferences)
    }

    @Provides
    @Singleton
    fun provideFilterPreferences(
        @ApplicationContext context: Context
    ): FilterPreferences {
        return FilterPreferences(context)
    }
}
