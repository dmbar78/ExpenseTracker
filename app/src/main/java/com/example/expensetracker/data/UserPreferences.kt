package com.example.expensetracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        
        private val GEMINI_ENABLED = booleanPreferencesKey("gemini_enabled")
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val OVERRIDE_DEFAULT_ACCOUNT_WITH_FILTER = booleanPreferencesKey("override_default_account_with_filter")
        
        // Initial default currency on first app install
        const val INITIAL_DEFAULT_CURRENCY = "EUR"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
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
    
    val isGeminiEnabled: Flow<Boolean> = context.userPreferencesDataStore.data.map { prefs ->
        prefs[GEMINI_ENABLED] ?: false
    }

    val geminiApiKey: Flow<String> = context.userPreferencesDataStore.data.map { prefs ->
        prefs[GEMINI_API_KEY] ?: ""
    }

    val geminiModel: Flow<String> = context.userPreferencesDataStore.data.map { prefs ->
        prefs[GEMINI_MODEL] ?: DEFAULT_GEMINI_MODEL
    }

    val overrideDefaultAccountWithFilter: Flow<Boolean> = context.userPreferencesDataStore.data.map { prefs ->
        prefs[OVERRIDE_DEFAULT_ACCOUNT_WITH_FILTER] ?: false
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

    suspend fun setGeminiEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[GEMINI_ENABLED] = enabled
        }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[GEMINI_API_KEY] = apiKey
        }
    }

    suspend fun setGeminiModel(model: String) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[GEMINI_MODEL] = model
        }
    }

    suspend fun setOverrideDefaultAccountWithFilter(override: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[OVERRIDE_DEFAULT_ACCOUNT_WITH_FILTER] = override
        }
    }
}
