package com.example.expensetracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Manages user preferences using DataStore.
 * Includes default currency setting.
 */
class UserPreferences(private val context: Context) {
    
    companion object {
        private val DEFAULT_CURRENCY_CODE = stringPreferencesKey("default_currency_code")
        private val DEFAULT_EXPENSE_ACCOUNT_ID = intPreferencesKey("default_expense_account_id")
        private val DEFAULT_TRANSFER_ACCOUNT_ID = intPreferencesKey("default_transfer_account_id")
        
        // Initial default currency on first app install
        const val INITIAL_DEFAULT_CURRENCY = "EUR"
    }
    
    /**
     * Flow of the current default currency code.
     * Returns EUR if not set.
     */
    val defaultCurrencyCode: Flow<String> = context.userPreferencesDataStore.data.map { prefs ->
        prefs[DEFAULT_CURRENCY_CODE] ?: INITIAL_DEFAULT_CURRENCY
    }
    
    val defaultExpenseAccountId: Flow<Int?> = context.userPreferencesDataStore.data.map { prefs ->
        prefs[DEFAULT_EXPENSE_ACCOUNT_ID]
    }

    val defaultTransferAccountId: Flow<Int?> = context.userPreferencesDataStore.data.map { prefs ->
        prefs[DEFAULT_TRANSFER_ACCOUNT_ID]
    }
    
    /**
     * Update the default currency code.
     */
    suspend fun setDefaultCurrencyCode(currencyCode: String) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[DEFAULT_CURRENCY_CODE] = currencyCode
        }
    }
    
    suspend fun setDefaultExpenseAccountId(accountId: Int?) {
        context.userPreferencesDataStore.edit { prefs ->
            if (accountId == null) {
                prefs.remove(DEFAULT_EXPENSE_ACCOUNT_ID)
            } else {
                prefs[DEFAULT_EXPENSE_ACCOUNT_ID] = accountId
            }
        }
    }

    suspend fun setDefaultTransferAccountId(accountId: Int?) {
        context.userPreferencesDataStore.edit { prefs ->
            if (accountId == null) {
                prefs.remove(DEFAULT_TRANSFER_ACCOUNT_ID)
            } else {
                prefs[DEFAULT_TRANSFER_ACCOUNT_ID] = accountId
            }
        }
    }
}
