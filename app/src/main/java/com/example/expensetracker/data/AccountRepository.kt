package com.example.expensetracker.data

import kotlinx.coroutines.flow.Flow

class AccountRepository(private val accountDao: AccountDao) {

    val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts()

    suspend fun insert(account: Account) {
        accountDao.insert(account)
    }

    suspend fun update(account: Account) {
        accountDao.update(account)
    }

    suspend fun delete(account: Account) {
        accountDao.delete(account)
    }

    fun getAccountById(accountId: Int): Flow<Account> {
        return accountDao.getAccountById(accountId)
    }
}