package com.example.expensetracker.di

import com.example.expensetracker.sharedimport.DefaultSharedFileImportProcessor
import com.example.expensetracker.sharedimport.MlKitReceiptTextExtractor
import com.example.expensetracker.sharedimport.ReceiptTextExtractor
import com.example.expensetracker.sharedimport.SharedFileImportProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SharedImportModule {
    @Binds
    @Singleton
    abstract fun bindReceiptTextExtractor(implementation: MlKitReceiptTextExtractor): ReceiptTextExtractor

    @Binds
    @Singleton
    abstract fun bindSharedFileImportProcessor(
        implementation: DefaultSharedFileImportProcessor
    ): SharedFileImportProcessor
}