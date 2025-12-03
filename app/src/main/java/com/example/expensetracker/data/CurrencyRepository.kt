package com.example.expensetracker.data

import kotlinx.coroutines.flow.Flow

class CurrencyRepository(private val currencyDao: CurrencyDao) {

    val allCurrencies: Flow<List<Currency>> = currencyDao.getAllCurrencies()

    suspend fun insert(currency: Currency) {
        currencyDao.insert(currency)
    }

    suspend fun update(currency: Currency) {
        currencyDao.update(currency)
    }

    suspend fun delete(currency: Currency) {
        currencyDao.delete(currency)
    }
}