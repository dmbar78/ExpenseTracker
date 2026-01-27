package com.example.expensetracker.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.expensetracker.data.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExpenseViewModel(
    application: Application,
    private val expenseRepository: ExpenseRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val transferHistoryRepository: TransferHistoryRepository,
    private val ledgerRepository: LedgerRepository,
    private val filterPreferences: FilterPreferences,
    private val userPreferences: UserPreferences,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val backupRepository: BackupRepository,
    private val keywordDao: KeywordDao
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VoiceDateParse"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val database = AppDatabase.getDatabase(application)
                val userPrefs = UserPreferences(application)
                
                return ExpenseViewModel(
                    application,
                    ExpenseRepository(database.expenseDao()),
                    AccountRepository(database.accountDao()),
                    CategoryRepository(database.categoryDao()),
                    CurrencyRepository(database.currencyDao()),
                    TransferHistoryRepository(database.transferHistoryDao()),
                    LedgerRepository(database.ledgerDao()),
                    FilterPreferences(application),
                    userPrefs,
                    ExchangeRateRepository(database.exchangeRateDao(), FrankfurterRatesProvider()),
                    BackupRepository(database, userPrefs),
                    database.keywordDao()
                ) as T
            }
        }
    }

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    val allExpenses: StateFlow<List<Expense>>
    val allIncomes: StateFlow<List<Expense>>
    val allAccounts: StateFlow<List<Account>>
    val allCategories: StateFlow<List<Category>>
    val allCurrencies: StateFlow<List<Currency>>
    val allTransfers: StateFlow<List<TransferHistory>>
    val allKeywords: StateFlow<List<Keyword>>
    
    // Default currency
    private val _defaultCurrencyCode = MutableStateFlow(UserPreferences.INITIAL_DEFAULT_CURRENCY)
    val defaultCurrencyCode: StateFlow<String> = _defaultCurrencyCode.asStateFlow()
    
    // Filter state
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()
    
    // Filtered lists for HomeScreen
    val filteredExpenses: StateFlow<List<Expense>>
    val filteredIncomes: StateFlow<List<Expense>>
    val filteredTransfers: StateFlow<List<TransferHistory>>

    // For voice recognition state
    private val _voiceRecognitionState = MutableStateFlow<VoiceRecognitionState>(VoiceRecognitionState.Idle)
    val voiceRecognitionState: StateFlow<VoiceRecognitionState> = _voiceRecognitionState

    // For general errors and navigation
    private val _errorChannel = Channel<String>(Channel.BUFFERED)
    val errorFlow = _errorChannel.receiveAsFlow()

    private val _navigateBackChannel = Channel<Unit>(Channel.BUFFERED)
    val navigateBackFlow = _navigateBackChannel.receiveAsFlow()

    private val _navigateTo = Channel<String>(Channel.BUFFERED)
    val navigateToFlow = _navigateTo.receiveAsFlow()

    // For entity selection
    private val _selectedAccountId = MutableStateFlow<Int?>(null)
    val selectedAccount: StateFlow<Account?> = _selectedAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(null) else accountRepository.getAccountById(accountId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _selectedCategoryId = MutableStateFlow<Int?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategoryId.flatMapLatest { categoryId ->
        if (categoryId == null) flowOf(null) else categoryRepository.getCategoryById(categoryId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _selectedExpenseId = MutableStateFlow<Int?>(null)
    val selectedExpense: StateFlow<Expense?> = _selectedExpenseId.flatMapLatest { expenseId ->
        if (expenseId == null) flowOf(null) else expenseRepository.getExpenseById(expenseId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _selectedTransferId = MutableStateFlow<Int?>(null)
    val selectedTransfer: StateFlow<TransferHistory?> = _selectedTransferId.flatMapLatest { transferId ->
        if (transferId == null) flowOf(null) else transferHistoryRepository.getTransferById(transferId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _selectedCurrencyId = MutableStateFlow<Int?>(null)
    val selectedCurrency: StateFlow<Currency?> = _selectedCurrencyId.flatMapLatest { currencyId ->
        if (currencyId == null) flowOf(null) else currencyRepository.getCurrencyById(currencyId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    var pendingParsedData: Any? = null

    init {
        allExpenses = expenseRepository.getExpensesByType("Expense").stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allIncomes = expenseRepository.getExpensesByType("Income").stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allAccounts = accountRepository.allAccounts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allCategories = categoryRepository.allCategories.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allCurrencies = currencyRepository.allCurrencies.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allTransfers = transferHistoryRepository.allTransfers.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allKeywords = keywordDao.getAllKeywords().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
        // Build a map of expenseId -> list of keyword names for text query filtering
        val expenseKeywordNamesMap: StateFlow<Map<Int, List<String>>> = combine(
            keywordDao.getAllExpenseKeywordCrossRefs(),
            keywordDao.getAllKeywords()
        ) { crossRefs, keywords ->
            val keywordIdToName = keywords.associate { it.id to it.name }
            crossRefs.groupBy { it.expenseId }
                .mapValues { (_, refs) -> refs.mapNotNull { keywordIdToName[it.keywordId] } }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
        
        // Derive filtered expenses from allExpenses + filterState + keyword names
        filteredExpenses = combine(allExpenses, _filterState, expenseKeywordNamesMap) { expenses, filter, keywordMap ->
            applyExpenseFilters(expenses, filter, keywordMap)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
        // Derive filtered incomes from allIncomes + filterState + keyword names
        filteredIncomes = combine(allIncomes, _filterState, expenseKeywordNamesMap) { incomes, filter, keywordMap ->
            applyExpenseFilters(incomes, filter, keywordMap)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
        // Derive filtered transfers from allTransfers + filterState
        filteredTransfers = combine(allTransfers, _filterState) { transfers, filter ->
            applyTransferFilters(transfers, filter)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
        // Load persisted filter state
        viewModelScope.launch {
            filterPreferences.filterState.collect { savedState ->
                _filterState.value = savedState
            }
        }
        
        // Load persisted default currency
        viewModelScope.launch {
            userPreferences.defaultCurrencyCode.collect { currencyCode ->
                _defaultCurrencyCode.value = currencyCode
            }
        }

        // Pre-populate currencies
        viewModelScope.launch {
            if (currencyRepository.allCurrencies.first().isEmpty()) {
                currencyRepository.insert(Currency(code = "USD", name = "United States Dollar"))
                currencyRepository.insert(Currency(code = "EUR", name = "Euro"))
                currencyRepository.insert(Currency(code = "RUB", name = "Russian Ruble"))
                currencyRepository.insert(Currency(code = "CHF", name = "Swiss Franc"))
                currencyRepository.insert(Currency(code = "EGP", name = "Egyptian Pound"))
                currencyRepository.insert(Currency(code = "GBP", name = "British Pound"))
            }
            if (categoryRepository.allCategories.first().isEmpty()) {
                categoryRepository.insert(Category(name = "Default"))
            }
        }
    }

    fun onTabSelected(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun loadAccount(accountId: Int) { _selectedAccountId.value = accountId }
    fun loadCategory(categoryId: Int) { _selectedCategoryId.value = categoryId }
    fun loadExpense(expenseId: Int) { _selectedExpenseId.value = expenseId }
    fun loadTransfer(transferId: Int) { _selectedTransferId.value = transferId }
    fun loadCurrency(currencyId: Int) { _selectedCurrencyId.value = currencyId }

    // ==================== Keyword Methods ====================

    /**
     * Get the keyword IDs associated with a specific expense (one-time lookup).
     */
    suspend fun getKeywordIdsForExpense(expenseId: Int): Set<Int> {
        return keywordDao.getKeywordIdsForExpense(expenseId).toSet()
    }

    /**
     * Insert a new keyword and return its ID.
     * Trims whitespace from name before insert.
     */
    suspend fun insertKeyword(name: String): Long {
        return keywordDao.insert(Keyword(name = name.trim()))
    }

    // ==================== Filter Methods ====================
    
    /**
     * Apply filters to expenses/incomes based on current FilterState.
     * @param keywordNamesMap Map of expenseId to list of keyword names for text query matching
     */
    private fun applyExpenseFilters(
        expenses: List<Expense>,
        filter: FilterState,
        keywordNamesMap: Map<Int, List<String>>
    ): List<Expense> {
        var result = expenses
        
        // Apply time filter
        val timeRange = filter.timeFilter.toDateRange()
        if (timeRange != null) {
            result = result.filter { it.expenseDate in timeRange.first..timeRange.second }
        }
        
        // Apply account filter
        filter.expenseIncomeAccount?.let { account ->
            result = result.filter { it.account.equals(account, ignoreCase = true) }
        }
        
        // Apply category filter
        filter.category?.let { category ->
            result = result.filter { it.category.equals(category, ignoreCase = true) }
        }
        
        // Apply text query filter (matches comment or any keyword name)
        filter.textQuery?.let { query ->
            val lowerQuery = query.lowercase()
            result = result.filter { expense ->
                val commentMatches = expense.comment?.lowercase()?.contains(lowerQuery) == true
                val keywordMatches = keywordNamesMap[expense.id]?.any { it.lowercase().contains(lowerQuery) } == true
                commentMatches || keywordMatches
            }
        }
        
        return result
    }
    
    /**
     * Apply filters to transfers based on current FilterState.
     */
    private fun applyTransferFilters(transfers: List<TransferHistory>, filter: FilterState): List<TransferHistory> {
        var result = transfers
        
        // Apply time filter
        val timeRange = filter.timeFilter.toDateRange()
        if (timeRange != null) {
            result = result.filter { it.date in timeRange.first..timeRange.second }
        }
        
        // Apply source account filter
        filter.transferSourceAccount?.let { source ->
            result = result.filter { it.sourceAccount.equals(source, ignoreCase = true) }
        }
        
        // Apply destination account filter
        filter.transferDestAccount?.let { dest ->
            result = result.filter { it.destinationAccount.equals(dest, ignoreCase = true) }
        }
        
        // Apply text query filter (matches comment only - transfers have no keywords)
        filter.textQuery?.let { query ->
            val lowerQuery = query.lowercase()
            result = result.filter { transfer ->
                transfer.comment?.lowercase()?.contains(lowerQuery) == true
            }
        }
        
        return result
    }
    
    /**
     * Set the time filter and persist.
     */
    fun setTimeFilter(timeFilter: TimeFilter) {
        val newState = _filterState.value.copy(timeFilter = timeFilter)
        _filterState.value = newState
        viewModelScope.launch { filterPreferences.saveFilterState(newState) }
    }
    
    /**
     * Set the expense/income account filter and persist.
     */
    fun setExpenseIncomeAccountFilter(account: String?) {
        val newState = _filterState.value.copy(expenseIncomeAccount = account)
        _filterState.value = newState
        viewModelScope.launch { filterPreferences.saveFilterState(newState) }
    }
    
    /**
     * Set the category filter and persist.
     */
    fun setCategoryFilter(category: String?) {
        val newState = _filterState.value.copy(category = category)
        _filterState.value = newState
        viewModelScope.launch { filterPreferences.saveFilterState(newState) }
    }
    
    /**
     * Set the transfer source account filter and persist.
     */
    fun setTransferSourceAccountFilter(account: String?) {
        val newState = _filterState.value.copy(transferSourceAccount = account)
        _filterState.value = newState
        viewModelScope.launch { filterPreferences.saveFilterState(newState) }
    }
    
    /**
     * Set the transfer destination account filter and persist.
     */
    fun setTransferDestAccountFilter(account: String?) {
        val newState = _filterState.value.copy(transferDestAccount = account)
        _filterState.value = newState
        viewModelScope.launch { filterPreferences.saveFilterState(newState) }
    }
    
    /**
     * Set both transfer account filters at once and persist.
     */
    fun setTransferAccountFilters(sourceAccount: String?, destAccount: String?) {
        val newState = _filterState.value.copy(
            transferSourceAccount = sourceAccount,
            transferDestAccount = destAccount
        )
        _filterState.value = newState
        viewModelScope.launch { filterPreferences.saveFilterState(newState) }
    }
    
    /**
     * Reset all filters.
     */
    fun resetAllFilters() {
        _filterState.value = FilterState()
        viewModelScope.launch { filterPreferences.clearAllFilters() }
    }
    
    /**
     * Reset only the time filter.
     */
    fun resetTimeFilter() {
        setTimeFilter(TimeFilter.None)
    }
    
    /**
     * Reset only the expense/income account filter.
     */
    fun resetExpenseIncomeAccountFilter() {
        setExpenseIncomeAccountFilter(null)
    }
    
    /**
     * Reset only the category filter.
     */
    fun resetCategoryFilter() {
        setCategoryFilter(null)
    }
    
    /**
     * Reset only the transfer filters.
     */
    fun resetTransferFilters() {
        setTransferAccountFilters(null, null)
    }
    
    /**
     * Set the text query filter (comment/keyword search) and persist.
     * Trims whitespace; blank query is treated as null (no filter).
     */
    fun setTextQueryFilter(query: String?) {
        val trimmed = query?.trim()?.takeIf { it.isNotEmpty() }
        val newState = _filterState.value.copy(textQuery = trimmed)
        _filterState.value = newState
        viewModelScope.launch { filterPreferences.saveFilterState(newState) }
    }
    
    /**
     * Reset only the text query filter.
     */
    fun resetTextQueryFilter() {
        setTextQueryFilter(null)
    }

    // ==================== End Filter Methods ====================
    
    // ==================== Currency/Exchange Rate Methods ====================
    
    /**
     * Set the default currency and persist.
     */
    fun setDefaultCurrency(currencyCode: String) {
        _defaultCurrencyCode.value = currencyCode
        viewModelScope.launch { userPreferences.setDefaultCurrencyCode(currencyCode) }
    }
    
    /**
     * Get the exchange rate for a currency pair on a specific date.
     * Returns null if the rate is not available.
     */
    suspend fun getExchangeRate(baseCurrency: String, quoteCurrency: String, date: Long): BigDecimal? {
        return exchangeRateRepository.getRateOrNull(baseCurrency, quoteCurrency, date)
    }
    
    /**
     * Get the latest (most recent on or before today) rate for displaying "1 currency = X default".
     * Returns formatted string or null if no rate available.
     */
    suspend fun getLatestRateDisplay(currencyCode: String): String? {
        val defaultCurrency = _defaultCurrencyCode.value
        if (currencyCode == defaultCurrency) return null
        
        val today = System.currentTimeMillis()
        val rate = exchangeRateRepository.getMostRecentRateOnOrBefore(currencyCode, defaultCurrency, today)
        return rate?.let { "1 $currencyCode = ${it.setScale(4, RoundingMode.HALF_UP)} $defaultCurrency" }
    }
    
    /**
     * Check if EUR pivot rate exists for a currency (for default currency guard).
     * First tries cache, then attempts a fetch from provider.
     * Returns true if pivot exists or was successfully fetched.
     */
    suspend fun ensureEurPivotExists(currencyCode: String): Boolean {
        if (currencyCode == "EUR") return true
        
        val today = System.currentTimeMillis()
        
        // Check cache first
        if (exchangeRateRepository.hasEurPivotOnOrBefore(currencyCode, today)) {
            return true
        }
        
        // Try to fetch from provider
        return exchangeRateRepository.fetchAndStoreEurPivot(currencyCode, today)
    }
    
    /**
     * Set a manual EUR pivot rate for a currency.
     * Used when no pivot is available from the provider.
     */
    suspend fun setManualEurPivot(currencyCode: String, eurToX: BigDecimal) {
        val today = System.currentTimeMillis()
        exchangeRateRepository.setRate("EUR", currencyCode, today, eurToX)
    }
    
    /**
     * Set a manual exchange rate override for a currency to the current default currency.
     * This stores the appropriate EUR pivot row so cross-rate derivations remain consistent.
     */
    suspend fun setManualRateOverride(currencyCode: String, currencyToDefaultRate: BigDecimal) {
        val defaultCurrency = _defaultCurrencyCode.value
        val today = System.currentTimeMillis()
        
        if (defaultCurrency == "EUR") {
            // Store directly as EUR → currency
            val eurToCurrency = BigDecimal.ONE.divide(currencyToDefaultRate, 10, RoundingMode.HALF_UP)
            exchangeRateRepository.setRate("EUR", currencyCode, today, eurToCurrency)
        } else {
            // Need to compute EUR → currency from the chain:
            // currency → default rate given
            // We need EUR → currency
            // If we have EUR → default, then EUR → currency = (EUR → default) * (default → currency)
            // where default → currency = 1 / currencyToDefaultRate
            val eurToDefault = exchangeRateRepository.getMostRecentRateOnOrBefore("EUR", defaultCurrency, today)
            if (eurToDefault != null) {
                val eurToCurrency = eurToDefault.divide(currencyToDefaultRate, 10, RoundingMode.HALF_UP)
                exchangeRateRepository.setRate("EUR", currencyCode, today, eurToCurrency)
            } else {
                // Fallback: store the direct rate (less ideal but better than nothing)
                exchangeRateRepository.setRate(currencyCode, defaultCurrency, today, currencyToDefaultRate)
            }
        }
    }
    
    /**
     * Check if all required rates are available for calculating totals.
     * Returns true if all expenses/transfers can be converted to the current default currency.
     */
    suspend fun hasAllRatesForTotals(
        expenses: List<Expense>,
        transfers: List<TransferHistory>,
        currentDefault: String
    ): Boolean {
        // Check expenses
        for (expense in expenses) {
            val originalDefault = expense.originalDefaultCurrencyCode
            if (originalDefault == null) {
                // Legacy expense without snapshot - need rate from currency to current default
                if (!exchangeRateRepository.hasRate(expense.currency, currentDefault, expense.expenseDate)) {
                    return false
                }
            } else if (originalDefault != currentDefault) {
                // Need rebase rate from originalDefault to currentDefault
                if (!exchangeRateRepository.hasRate(originalDefault, currentDefault, expense.expenseDate)) {
                    return false
                }
            }
            // If originalDefault == currentDefault and snapshot exists, no rate needed
        }
        
        // Check transfers
        for (transfer in transfers) {
            val originalDefault = transfer.originalDefaultCurrencyCode
            if (originalDefault == null) {
                if (!exchangeRateRepository.hasRate(transfer.currency, currentDefault, transfer.date)) {
                    return false
                }
            } else if (originalDefault != currentDefault) {
                if (!exchangeRateRepository.hasRate(originalDefault, currentDefault, transfer.date)) {
                    return false
                }
            }
        }
        
        return true
    }
    
    /**
     * Calculate total for expenses in the current default currency.
     * Returns null if any required rate is missing.
     */
    suspend fun calculateExpensesTotal(expenses: List<Expense>, currentDefault: String): BigDecimal? {
        var total = BigDecimal.ZERO
        
        for (expense in expenses) {
            val amountInDefault = getAmountInCurrentDefault(
                amount = expense.amount,
                currency = expense.currency,
                date = expense.expenseDate,
                originalDefaultCurrency = expense.originalDefaultCurrencyCode,
                amountInOriginalDefault = expense.amountInOriginalDefault,
                currentDefault = currentDefault
            ) ?: return null
            
            total = total.add(amountInDefault)
        }
        
        return total
    }
    
    /**
     * Calculate total for transfers in the current default currency.
     * Returns null if any required rate is missing.
     */
    suspend fun calculateTransfersTotal(transfers: List<TransferHistory>, currentDefault: String): BigDecimal? {
        var total = BigDecimal.ZERO
        
        for (transfer in transfers) {
            val amountInDefault = getAmountInCurrentDefault(
                amount = transfer.amount,
                currency = transfer.currency,
                date = transfer.date,
                originalDefaultCurrency = transfer.originalDefaultCurrencyCode,
                amountInOriginalDefault = transfer.amountInOriginalDefault,
                currentDefault = currentDefault
            ) ?: return null
            
            total = total.add(amountInDefault)
        }
        
        return total
    }
    
    /**
     * Convert an amount to the current default currency.
     * Uses snapshot if available, otherwise fetches rate.
     * Uses "most recent on or before" rate lookup to handle missing exact-day rates.
     * Returns null if required rate is missing.
     */
    private suspend fun getAmountInCurrentDefault(
        amount: BigDecimal,
        currency: String,
        date: Long,
        originalDefaultCurrency: String?,
        amountInOriginalDefault: BigDecimal?,
        currentDefault: String
    ): BigDecimal? {
        // If transaction is already in current default currency
        if (currency == currentDefault) {
            return amount
        }
        
        // If we have a snapshot and the original default matches current default
        if (amountInOriginalDefault != null && originalDefaultCurrency == currentDefault) {
            return amountInOriginalDefault
        }
        
        // If we have a snapshot but need to rebase to different current default
        if (amountInOriginalDefault != null && originalDefaultCurrency != null) {
            val rebaseRate = exchangeRateRepository.getMostRecentRateOnOrBefore(originalDefaultCurrency, currentDefault, date)
                ?: return null
            return amountInOriginalDefault.multiply(rebaseRate)
        }
        
        // No snapshot - convert directly from transaction currency to current default
        val rate = exchangeRateRepository.getMostRecentRateOnOrBefore(currency, currentDefault, date)
            ?: return null
        return amount.multiply(rate)
    }
    
    /**
     * Get the exchange rate for converting an account balance to the current default currency.
     * Uses "most recent on or before today" to handle cases where exact-day rates
     * are not available (e.g., weekends, holidays, or manual overrides from earlier dates).
     */
    suspend fun getAccountConversionRate(accountCurrency: String, currentDefault: String): BigDecimal? {
        if (accountCurrency == currentDefault) return BigDecimal.ONE
        return exchangeRateRepository.getMostRecentRateOnOrBefore(accountCurrency, currentDefault, System.currentTimeMillis())
    }
    
    // ==================== End Currency/Exchange Rate Methods ====================

    fun onVoiceRecognitionResult(spokenText: String) {
        viewModelScope.launch {
            val parsedTransfer = parseTransfer(spokenText)
            if (parsedTransfer != null) {
                pendingParsedData = parsedTransfer
                processParsedTransfer(parsedTransfer)
                return@launch
            }

            val parsedExpense = parseExpense(spokenText)
            if (parsedExpense != null) {
                pendingParsedData = parsedExpense
                processParsedExpense(parsedExpense)
                return@launch
            }
            
            _voiceRecognitionState.value = VoiceRecognitionState.RecognitionFailed("""Couldn't recognize input: '$spokenText'. Please repeat in one of the formats (example values used):
            1. Expense from AccountName 50 Category Food (optional date if not today).
            2. Income to AccountName 50 Category Salary (optional date if not today).
            3. Transfer from SourceAccountName to DestinationAccountName 50 (optional date if not today).
            
            Optional date format can be:
            - January 1
            - January 1st
            - 1st of January.""")

        }
    }

    private suspend fun processParsedTransfer(parsedTransfer: ParsedTransfer) {
        val accounts = allAccounts.first()
        if (accounts.isEmpty()) {
            _voiceRecognitionState.value = VoiceRecognitionState.RecognitionFailed(
                "No accounts found. Please create at least one account before adding transfers."
            )
            return
        }
        
        val sourceAccount = accounts.find { it.name.equals(parsedTransfer.sourceAccountName, ignoreCase = true) }
        val destAccount = accounts.find { it.name.equals(parsedTransfer.destAccountName, ignoreCase = true) }

        if (sourceAccount == null || destAccount == null) {
            val sourceError = sourceAccount == null
            val destError = destAccount == null
            // Use canonical DB names (correct casing) when found, otherwise show parsed text so user sees what was recognized
            val sourceName = if (sourceError) parsedTransfer.sourceAccountName else sourceAccount.name
            val destName = if (destError) parsedTransfer.destAccountName else destAccount.name
            
            _navigateTo.send(
                "editTransfer/0" +
                    "?sourceAccountName=${Uri.encode(sourceName)}" +
                    "&destAccountName=${Uri.encode(destName)}" +
                    "&amount=${Uri.encode(parsedTransfer.amount.toPlainString())}" +
                    "&transferDateMillis=${parsedTransfer.transferDate}" +
                    "&sourceAccountError=$sourceError" +
                    "&destAccountError=$destError"
            )
            return
        }

        val transferRecord = TransferHistory(
            sourceAccount = sourceAccount.name,
            destinationAccount = destAccount.name,
            amount = parsedTransfer.amount.setScale(2, RoundingMode.HALF_UP),
            currency = sourceAccount.currency,
            comment = parsedTransfer.comment,
            date = parsedTransfer.transferDate
        )

        try {
            ledgerRepository.addTransfer(transferRecord)
            _voiceRecognitionState.value = VoiceRecognitionState.Success("Transfer from ${parsedTransfer.sourceAccountName} to ${parsedTransfer.destAccountName} for ${parsedTransfer.amount} ${sourceAccount.currency} on ${formatDate(transferRecord.date)} successfully added.")
        } catch (e: IllegalArgumentException) {
            val message = e.message ?: "Invalid transfer."
            if (message.contains("same", ignoreCase = true)) {
                _voiceRecognitionState.value = VoiceRecognitionState.SameAccountTransfer(message)
            } else if (message.contains("currency", ignoreCase = true)) {
                _voiceRecognitionState.value = VoiceRecognitionState.TransferCurrencyMismatch(message)
            } else {
                _voiceRecognitionState.value = VoiceRecognitionState.RecognitionFailed(message)
            }
        } catch (e: Exception) {
            _voiceRecognitionState.value = VoiceRecognitionState.RecognitionFailed(e.message ?: "Failed to add transfer.")
        }
    }

    private suspend fun processParsedExpense(parsedExpense: ParsedExpense) {
        val accounts = allAccounts.first() // Wait for data to be loaded, but proceed even if empty
        val account = accounts.find { it.name.equals(parsedExpense.accountName, ignoreCase = true) }
        
        // Wait for categories to be loaded (Default is pre-populated)
        val categories = allCategories.first { it.isNotEmpty() }
        val category = categories.find { it.name.equals(parsedExpense.categoryName, ignoreCase = true) }

        if (account == null || category == null) {
            val accountError = account == null
            val categoryError = category == null
            // Use canonical DB names (correct casing) when found, otherwise show parsed text so user sees what was recognized
            val accountName = if (accountError) parsedExpense.accountName else account.name
            val categoryName = if (categoryError) parsedExpense.categoryName else category.name
            val expenseType = parsedExpense.type ?: if (_selectedTab.value == 0) "Expense" else "Income"
            
            _navigateTo.send(
                "editExpense/0" +
                    "?accountName=${Uri.encode(accountName)}" +
                    "&amount=${Uri.encode(parsedExpense.amount.toPlainString())}" +
                    "&categoryName=${Uri.encode(categoryName)}" +
                    "&type=${Uri.encode(expenseType)}" +
                    "&expenseDateMillis=${parsedExpense.expenseDate}" +
                    "&accountError=$accountError" +
                    "&categoryError=$categoryError"
            )
            return
        }

        val expenseType = parsedExpense.type ?: if (_selectedTab.value == 0) "Expense" else "Income"

        val finalExpense = Expense(
            account = account.name,
            amount = parsedExpense.amount.setScale(2, RoundingMode.HALF_UP),
            currency = account.currency,
            category = category.name,
            expenseDate = parsedExpense.expenseDate,
            type = expenseType
        )
        insertExpense(finalExpense)
        _voiceRecognitionState.value = VoiceRecognitionState.Success("${expenseType} of ${finalExpense.amount} ${finalExpense.currency} on ${formatDate(finalExpense.expenseDate)} for account ${finalExpense.account} and category ${finalExpense.category} successfully added.")
    }

    /**
     * Formats a date (epoch millis) to dd.MM.yyyy for user-facing messages.
     */
    private fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun parseTransfer(input: String): ParsedTransfer? {
        val lowerInput = input.lowercase(Locale.ROOT)
        val transferIndex = lowerInput.indexOf("transfer from ")
        val toIndex = lowerInput.indexOf(" to ")

        if (transferIndex == -1 || toIndex == -1 || !(transferIndex < toIndex)) {
            return null
        }

        val sourceAccountStr = input.substring(transferIndex + 14, toIndex).trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val restAfterTo = input.substring(toIndex + 4).trim()

        // 1) Parse (and remove) trailing date first so day numbers (e.g., "1st") don't get treated as amount
        val (restWithoutDate, transferDate) = parseTrailingSpokenDate(restAfterTo)

        // Regex that handles both dot and comma decimal separators, and thousand separators
        val amountRegex = Regex("([\\d,]+\\.?\\d*|[\\d.]+,?\\d*)")
        val amountMatch = amountRegex.findAll(restWithoutDate).lastOrNull() ?: return null
        val amount = parseMoneyAmount(amountMatch.value) ?: return null

        val destAccountStr = restWithoutDate.substring(0, amountMatch.range.first).trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        return ParsedTransfer(
            sourceAccountName = sourceAccountStr,
            destAccountName = destAccountStr,
            amount = amount,
            transferDate = transferDate,
            comment = null
        )
    }

    private fun parseExpense(input: String): ParsedExpense? {
        val lowerInput = input.lowercase(Locale.ROOT)

        val expenseKeyword = "expense from "
        val incomeKeyword = "income to "

        val expenseIndex = lowerInput.indexOf(expenseKeyword)
        val incomeIndex = lowerInput.indexOf(incomeKeyword)

        val type: String
        val typeIndex: Int

        if (expenseIndex != -1) {
            type = "Expense"
            typeIndex = expenseIndex + expenseKeyword.length
        } else if (incomeIndex != -1) {
            type = "Income"
            typeIndex = incomeIndex + incomeKeyword.length
        } else {
            return null
        }

        val categoryIndex = lowerInput.indexOf(" category ", startIndex = typeIndex)
        if (categoryIndex == -1) {
            return null
        }

        val accountAndAmountBlock = input.substring(typeIndex, categoryIndex).trim()
        val categoryStr = input.substring(categoryIndex + 10).trim()

        // Regex that handles both dot and comma decimal separators, and thousand separators
        val amountRegex = Regex("([\\d,]+\\.?\\d*|[\\d.]+,?\\d*)")
        val amountMatch = amountRegex.findAll(accountAndAmountBlock).lastOrNull()

        if (amountMatch == null) {
            return null
        }

        val accountStr = accountAndAmountBlock.substring(0, amountMatch.range.first).trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val amountStr = amountMatch.value

        val amount = parseMoneyAmount(amountStr)
        if (amount == null) {
            return null
        }

        // Parse optional trailing date from category string
        val (finalCategoryStr, parsedDate) = parseTrailingSpokenDate(categoryStr)
        val expenseDate = parsedDate ?: System.currentTimeMillis()

        return ParsedExpense(
            accountName = accountStr, 
            amount = amount, 
            categoryName = finalCategoryStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, 
            type = type, 
            expenseDate = expenseDate
        )
    }

    fun reprocessExpenseWithNewCategory(newCategoryName: String) {
        (pendingParsedData as? ParsedExpense)?.let { 
            viewModelScope.launch {
                processParsedExpense(it.copy(categoryName = newCategoryName))
            }
        }
    }

    fun dismissVoiceRecognitionDialog() {
        _voiceRecognitionState.value = VoiceRecognitionState.Idle
    }

    fun deleteAccount(account: Account) = viewModelScope.launch {
        val expenseCount = expenseRepository.getCountByAccount(account.name)
        val transferCount = transferHistoryRepository.getCountByAccount(account.name)

        if (expenseCount > 0 || transferCount > 0) {
            _errorChannel.send("Cannot delete account. It has associated expenses or transfers.")
        } else {
            accountRepository.delete(account)
            _navigateBackChannel.send(Unit)
        }
    }

    fun deleteCategory(category: Category) = viewModelScope.launch {
        categoryRepository.delete(category)
    }

    fun deleteCurrency(currency: Currency) = viewModelScope.launch {
        currencyRepository.delete(currency)
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        try {
            ledgerRepository.deleteExpense(expense.id)
        } catch (e: IllegalStateException) {
            _errorChannel.send(e.message ?: "Failed to delete expense.")
        }
    }

    fun deleteTransfer(transfer: TransferHistory) = viewModelScope.launch {
        try {
            ledgerRepository.deleteTransfer(transfer.id)
        } catch (e: IllegalStateException) {
            _errorChannel.send(e.message ?: "Failed to delete transfer.")
        }
    }

    fun insertExpense(expense: Expense) = viewModelScope.launch {
        try {
            ledgerRepository.addExpense(expense)
            _navigateBackChannel.send(Unit)
        } catch (e: IllegalStateException) {
            _errorChannel.send(e.message ?: "Failed to add expense.")
        }
    }

    /**
     * Insert expense and return result directly (for local navigation handling).
     */
    suspend fun insertExpenseAndReturn(expense: Expense): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            ledgerRepository.addExpense(expense)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Insert a new expense with associated keywords.
     */
    fun insertExpenseWithKeywords(expense: Expense, keywordIds: Set<Int>) = viewModelScope.launch {
        try {
            val expenseId = ledgerRepository.addExpense(expense)
            if (keywordIds.isNotEmpty()) {
                keywordDao.setKeywordsForExpense(expenseId.toInt(), keywordIds)
            }
            _navigateBackChannel.send(Unit)
        } catch (e: IllegalStateException) {
            _errorChannel.send(e.message ?: "Failed to add expense.")
        }
    }

    fun updateExpense(expense: Expense) = viewModelScope.launch {
        try {
            ledgerRepository.updateExpense(expense)
            _navigateBackChannel.send(Unit)
        } catch (e: IllegalStateException) {
            _errorChannel.send(e.message ?: "Failed to update expense.")
        }
    }

    /**
     * Update expense and return result directly (for local navigation handling).
     */
    suspend fun updateExpenseAndReturn(expense: Expense): Result<Unit> {
        return try {
            ledgerRepository.updateExpense(expense)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing expense with associated keywords.
     */
    fun updateExpenseWithKeywords(expense: Expense, keywordIds: Set<Int>) = viewModelScope.launch {
        try {
            ledgerRepository.updateExpense(expense)
            keywordDao.setKeywordsForExpense(expense.id, keywordIds)
            _navigateBackChannel.send(Unit)
        } catch (e: IllegalStateException) {
            _errorChannel.send(e.message ?: "Failed to update expense.")
        }
    }

    /**
     * Insert expense with keywords and return result directly (for local navigation handling).
     */
    suspend fun insertExpenseWithKeywordsAndReturn(expense: Expense, keywordIds: Set<Int>): Result<Unit> {
        return try {
            val expenseId = ledgerRepository.addExpense(expense)
            if (keywordIds.isNotEmpty()) {
                keywordDao.setKeywordsForExpense(expenseId.toInt(), keywordIds)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update expense with keywords and return result directly (for local navigation handling).
     */
    suspend fun updateExpenseWithKeywordsAndReturn(expense: Expense, keywordIds: Set<Int>): Result<Unit> {
        return try {
            ledgerRepository.updateExpense(expense)
            keywordDao.setKeywordsForExpense(expense.id, keywordIds)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateTransfer(transfer: TransferHistory) = viewModelScope.launch {
        try {
            ledgerRepository.updateTransfer(transfer)
            _navigateBackChannel.send(Unit)
        } catch (e: IllegalArgumentException) {
            _errorChannel.send(e.message ?: "Invalid transfer.")
        } catch (e: IllegalStateException) {
            _errorChannel.send(e.message ?: "Failed to update transfer.")
        }
    }

    fun insertTransfer(transfer: TransferHistory) = viewModelScope.launch {
        try {
            ledgerRepository.addTransfer(transfer)
            _navigateBackChannel.send(Unit)
        } catch (e: IllegalArgumentException) {
            _errorChannel.send(e.message ?: "Invalid transfer.")
        } catch (e: IllegalStateException) {
            _errorChannel.send(e.message ?: "Failed to add transfer.")
        }
    }

    /**
     * Insert transfer and return result directly (for screens that handle navigation locally).
     */
    suspend fun insertTransferAndReturn(transfer: TransferHistory): Result<Unit> {
        return try {
            ledgerRepository.addTransfer(transfer)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update transfer and return result directly (for screens that handle navigation locally).
     */
    suspend fun updateTransferAndReturn(transfer: TransferHistory): Result<Unit> {
        return try {
            ledgerRepository.updateTransfer(transfer)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Data-only version used internally (e.g. voice transfer) - no navigation
    private suspend fun updateAccountInternal(account: Account): Boolean {
        val trimmedAccount = account.copy(name = account.name.trim())
        if (trimmedAccount.name.isBlank() || trimmedAccount.currency.isBlank()) {
            _errorChannel.send("Account Name and Currency cannot be empty.")
            return false
        }
        return try {
            accountRepository.update(trimmedAccount)
            true
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _errorChannel.send("Account with name '${trimmedAccount.name}' already exists.")
            false
        } catch (e: Exception) {
            _errorChannel.send("An unknown error occurred.")
            false
        }
    }

    fun insertAccount(account: Account) = viewModelScope.launch {
        val trimmedAccount = account.copy(name = account.name.trim())
        if (trimmedAccount.name.isBlank() || trimmedAccount.currency.isBlank()) {
            _errorChannel.send("Account Name and Currency cannot be empty.")
            return@launch
        }
        try {
            accountRepository.insert(trimmedAccount)
            _navigateBackChannel.send(Unit)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _errorChannel.send("Account with name '${trimmedAccount.name}' already exists.")
        } catch (e: Exception) {
            _errorChannel.send("An unknown error occurred.")
        }
    }

    /**
     * Insert account and return result directly (for screens that handle navigation locally).
     * This avoids using the shared navigateBackChannel which can cause race conditions
     * when multiple screens are collecting from it.
     */
    suspend fun insertAccountAndReturn(account: Account): Result<Unit> {
        val trimmedAccount = account.copy(name = account.name.trim())
        if (trimmedAccount.name.isBlank() || trimmedAccount.currency.isBlank()) {
            return Result.failure(IllegalArgumentException("Account Name and Currency cannot be empty."))
        }
        return try {
            accountRepository.insert(trimmedAccount)
            Result.success(Unit)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            Result.failure(IllegalArgumentException("Account with name '${trimmedAccount.name}' already exists."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateAccount(account: Account) = viewModelScope.launch {
        // Fetch existing account to check for name change
        val oldAccount = accountRepository.getAccountById(account.id).firstOrNull()

        if (updateAccountInternal(account)) {
            // If update succeeded, check if name changed and cascade
            if (oldAccount != null && oldAccount.name != account.name.trim()) {
                val newName = account.name.trim()
                expenseRepository.updateAccountName(oldAccount.name, newName)
                transferHistoryRepository.updateAccountName(oldAccount.name, newName)
            }
            _navigateBackChannel.send(Unit)
        }
    }

    fun insertCategory(category: Category) = viewModelScope.launch {
        val trimmedCategory = category.copy(name = category.name.trim())
        if (trimmedCategory.name.isBlank()) {
            _errorChannel.send("Category Name cannot be empty.")
            return@launch
        }
        try {
            categoryRepository.insert(trimmedCategory)
            _navigateBackChannel.send(Unit)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _errorChannel.send("Category with name '${trimmedCategory.name}' already exists.")
        } catch (e: Exception) {
            _errorChannel.send("An unknown error occurred.")
        }
    }

    fun updateCategory(category: Category) = viewModelScope.launch {
        val oldCategory = categoryRepository.getCategoryById(category.id).firstOrNull()
        val trimmedCategory = category.copy(name = category.name.trim())
        
        if (trimmedCategory.name.isBlank()) {
            _errorChannel.send("Category Name cannot be empty.")
            return@launch
        }
        try {
            categoryRepository.update(trimmedCategory)
            
            if (oldCategory != null && oldCategory.name != trimmedCategory.name) {
                expenseRepository.updateCategoryName(oldCategory.name, trimmedCategory.name)
            }
            
            _navigateBackChannel.send(Unit)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _errorChannel.send("Category with name '${trimmedCategory.name}' already exists.")
        } catch (e: Exception) {
            _errorChannel.send("An unknown error occurred.")
        }
    }

    fun insertCurrency(currency: Currency) = viewModelScope.launch {
        val trimmedCurrency = currency.copy(code = currency.code.trim(), name = currency.name.trim())
        if (trimmedCurrency.code.isBlank() || trimmedCurrency.name.isBlank()) {
            _errorChannel.send("Code and Name cannot be empty.")
            return@launch
        }
        try {
            currencyRepository.insert(trimmedCurrency)
            _navigateBackChannel.send(Unit)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _errorChannel.send("Currency with code '${trimmedCurrency.code}' already exists.")
        } catch (e: Exception) {
            _errorChannel.send("An unknown error occurred.")
        }
    }

    fun updateCurrency(currency: Currency) = viewModelScope.launch {
        currencyRepository.update(currency)
    }

    /**
     * Parses a money amount string handling both dot and comma decimal separators.
     * Supports formats like: "1234.56", "1,234.56", "1234,56", "1.234,56"
     * Returns a BigDecimal with scale 2, or null if parsing fails.
     */
    private fun parseMoneyAmount(input: String): BigDecimal? {
        if (input.isBlank()) return null
        
        val cleaned = input.trim()
        
        // Determine which format is used based on the last separator
        val lastDotIndex = cleaned.lastIndexOf('.')
        val lastCommaIndex = cleaned.lastIndexOf(',')
        
        val normalizedString = when {
            // No separators - just digits
            lastDotIndex == -1 && lastCommaIndex == -1 -> cleaned
            // Only dots - could be decimal or thousand separator
            lastCommaIndex == -1 -> {
                // If there's only one dot and it has 1-2 digits after it, treat as decimal
                val afterDot = cleaned.length - lastDotIndex - 1
                if (cleaned.count { it == '.' } == 1 && afterDot <= 2) {
                    cleaned // e.g., "1234.56"
                } else {
                    // Multiple dots or > 2 digits after = thousand separators, remove them
                    cleaned.replace(".", "") // e.g., "1.234.567" -> "1234567"
                }
            }
            // Only commas - could be decimal or thousand separator
            lastDotIndex == -1 -> {
                // If there's only one comma and it has 1-2 digits after it, treat as decimal
                val afterComma = cleaned.length - lastCommaIndex - 1
                if (cleaned.count { it == ',' } == 1 && afterComma <= 2) {
                    cleaned.replace(',', '.') // e.g., "1234,56" -> "1234.56"
                } else {
                    // Multiple commas or > 2 digits after = thousand separators, remove them
                    cleaned.replace(",", "") // e.g., "1,234,567" -> "1234567"
                }
            }
            // Both dot and comma present
            lastDotIndex > lastCommaIndex -> {
                // Dot is the decimal separator (US format: 1,234.56)
                cleaned.replace(",", "") // Remove thousand separators
            }
            else -> {
                // Comma is the decimal separator (EU format: 1.234,56)
                cleaned.replace(".", "").replace(',', '.')
            }
        }
        
        return try {
            BigDecimal(normalizedString).setScale(2, RoundingMode.HALF_UP)
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Parses a trailing date phrase from spoken text.
     * Supports formats like:
     * - "January 1", "January 1st", "January first"
     * - "1st of January", "first of January"
     * - "1 January", "1st January"
     * Returns a Pair of (text with date removed, epoch millis) or (original text, default millis) if no date found.
     */
    private fun parseTrailingSpokenDate(input: String, defaultMillis: Long = System.currentTimeMillis()): Pair<String, Long> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            Log.d(TAG, "parseTrailingSpokenDate: blank input; returning defaultMillis=$defaultMillis")
            return Pair(trimmed, defaultMillis)
        }

        // Month names (English)
        val months = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
        )

        // Ordinal words to numbers
        val ordinalWords = mapOf(
            "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
            "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10,
            "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13, "fourteenth" to 14,
            "fifteenth" to 15, "sixteenth" to 16, "seventeenth" to 17, "eighteenth" to 18,
            "nineteenth" to 19, "twentieth" to 20, "twenty first" to 21, "twenty-first" to 21,
            "twenty second" to 22, "twenty-second" to 22, "twenty third" to 23, "twenty-third" to 23,
            "twenty fourth" to 24, "twenty-fourth" to 24, "twenty fifth" to 25, "twenty-fifth" to 25,
            "twenty sixth" to 26, "twenty-sixth" to 26, "twenty seventh" to 27, "twenty-seventh" to 27,
            "twenty eighth" to 28, "twenty-eighth" to 28, "twenty ninth" to 29, "twenty-ninth" to 29,
            "thirtieth" to 30, "thirty first" to 31, "thirty-first" to 31
        )

        val lowerInput = trimmed.lowercase(Locale.ROOT)

        Log.d(TAG, "parseTrailingSpokenDate: input='$input' trimmed='$trimmed' lower='$lowerInput' defaultMillis=$defaultMillis (${formatDate(defaultMillis)})")

        // Try to find a date pattern at the end
        // Pattern 1: "<month> <day>" e.g., "January 1", "January 1st", "January first"
        // Pattern 2: "<day> of <month>" e.g., "1st of January", "first of January"
        // Pattern 3: "<day> <month>" e.g., "1 January", "1st January"

        for ((monthIndex, monthName) in months.withIndex()) {
            Log.d(TAG, "--- Month loop: month='$monthName' index=$monthIndex")
            // Pattern 1: month followed by day at end
            val monthDayRegex = Regex("\\s+$monthName\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s*$", RegexOption.IGNORE_CASE)
            val monthDayMatch = monthDayRegex.find(lowerInput)
            Log.d(TAG, "Pattern1 <month> <day>: regex='${monthDayRegex.pattern}' match='${monthDayMatch?.value}'")
            if (monthDayMatch != null) {
                val day = monthDayMatch.groupValues[1].toIntOrNull()
                Log.d(TAG, "Pattern1 parse: rawDay='${monthDayMatch.groupValues[1]}' parsedDay=$day")
                if (day != null && day in 1..31) {
                    val strippedText = trimmed.substring(0, monthDayMatch.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day)
                    Log.d(TAG, "Pattern1 MATCH: month='$monthName' day=$day -> millis=$millis (${formatDate(millis)}) strippedText='$strippedText'")
                    return Pair(strippedText, millis)
                }
            }

            // Pattern 1b: month followed by ordinal word at end
            var pattern1bMatched = false
            for ((ordinalWord, day) in ordinalWords) {
                val pattern = Regex("\\s+$monthName\\s+$ordinalWord\\s*$", RegexOption.IGNORE_CASE)
                val match = pattern.find(lowerInput)
                if (match != null) {
                    val strippedText = trimmed.substring(0, match.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day)
                    Log.d(TAG, "Pattern1b <month> <ordinal>: MATCH month='$monthName' ordinal='$ordinalWord' day=$day -> millis=$millis (${formatDate(millis)}) strippedText='$strippedText'")
                    pattern1bMatched = true
                    return Pair(strippedText, millis)
                }
            }
            if (!pattern1bMatched) {
                Log.d(TAG, "Pattern1b <month> <ordinal>: no match")
            }

            // Pattern 2: "<day> of <month>" at end
            val dayOfMonthRegex = Regex("\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+of\\s+$monthName\\s*$", RegexOption.IGNORE_CASE)
            val dayOfMonthMatch = dayOfMonthRegex.find(lowerInput)
            Log.d(TAG, "Pattern2 <day> of <month>: regex='${dayOfMonthRegex.pattern}' match='${dayOfMonthMatch?.value}'")
            if (dayOfMonthMatch != null) {
                val day = dayOfMonthMatch.groupValues[1].toIntOrNull()
                Log.d(TAG, "Pattern2 parse: rawDay='${dayOfMonthMatch.groupValues[1]}' parsedDay=$day")
                if (day != null && day in 1..31) {
                    val strippedText = trimmed.substring(0, dayOfMonthMatch.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day)
                    Log.d(TAG, "Pattern2 MATCH: month='$monthName' day=$day -> millis=$millis (${formatDate(millis)}) strippedText='$strippedText'")
                    return Pair(strippedText, millis)
                }
            }

            // Pattern 2b: ordinal word + "of" + month at end
            var pattern2bMatched = false
            for ((ordinalWord, day) in ordinalWords) {
                val pattern = Regex("\\s+$ordinalWord\\s+of\\s+$monthName\\s*$", RegexOption.IGNORE_CASE)
                val match = pattern.find(lowerInput)
                if (match != null) {
                    val strippedText = trimmed.substring(0, match.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day)
                    Log.d(TAG, "Pattern2b <ordinal> of <month>: MATCH month='$monthName' ordinal='$ordinalWord' day=$day -> millis=$millis (${formatDate(millis)}) strippedText='$strippedText'")
                    pattern2bMatched = true
                    return Pair(strippedText, millis)
                }
            }
            if (!pattern2bMatched) {
                Log.d(TAG, "Pattern2b <ordinal> of <month>: no match")
            }

            // Pattern 3: "<day> <month>" at end (no "of")
            val dayMonthRegex = Regex("\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+$monthName\\s*$", RegexOption.IGNORE_CASE)
            val dayMonthMatch = dayMonthRegex.find(lowerInput)
            Log.d(TAG, "Pattern3 <day> <month>: regex='${dayMonthRegex.pattern}' match='${dayMonthMatch?.value}'")
            if (dayMonthMatch != null) {
                val day = dayMonthMatch.groupValues[1].toIntOrNull()
                Log.d(TAG, "Pattern3 parse: rawDay='${dayMonthMatch.groupValues[1]}' parsedDay=$day")
                if (day != null && day in 1..31) {
                    val strippedText = trimmed.substring(0, dayMonthMatch.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day)
                    Log.d(TAG, "Pattern3 MATCH: month='$monthName' day=$day -> millis=$millis (${formatDate(millis)}) strippedText='$strippedText'")
                    return Pair(strippedText, millis)
                }
            }

            // Pattern 3b: ordinal word + month at end (no "of")
            var pattern3bMatched = false
            for ((ordinalWord, day) in ordinalWords) {
                val pattern = Regex("\\s+$ordinalWord\\s+$monthName\\s*$", RegexOption.IGNORE_CASE)
                val match = pattern.find(lowerInput)
                if (match != null) {
                    val strippedText = trimmed.substring(0, match.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day)
                    Log.d(TAG, "Pattern3b <ordinal> <month>: MATCH month='$monthName' ordinal='$ordinalWord' day=$day -> millis=$millis (${formatDate(millis)}) strippedText='$strippedText'")
                    pattern3bMatched = true
                    return Pair(strippedText, millis)
                }
            }
            if (!pattern3bMatched) {
                Log.d(TAG, "Pattern3b <ordinal> <month>: no match")
            }
        }

        // No date pattern found
        Log.d(TAG, "No date pattern found; returning original text and defaultMillis=$defaultMillis (${formatDate(defaultMillis)})")
        return Pair(trimmed, defaultMillis)
    }

    // ==================== Backup/Restore Methods ====================

    private val _backupState = MutableStateFlow<BackupOperationState>(BackupOperationState.Idle)
    val backupState: StateFlow<BackupOperationState> = _backupState.asStateFlow()

    fun resetBackupState() {
        _backupState.value = BackupOperationState.Idle
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _backupState.value = BackupOperationState.Loading
            try {
                val backupData = backupRepository.exportBackupData()
                val json = backupRepository.serializeToJson(backupData)

                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.writer().use { writer ->
                        writer.write(json)
                    }
                }
                _backupState.value = BackupOperationState.Success("Backup exported successfully")
            } catch (e: Exception) {
                _backupState.value = BackupOperationState.Error("Export failed: ${e.localizedMessage}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _backupState.value = BackupOperationState.Loading
            try {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.reader().use { reader ->
                        reader.readText()
                    }
                }

                if (json == null) {
                    _backupState.value = BackupOperationState.Error("Failed to read backup file")
                    return@launch
                }

                val backupData = backupRepository.deserializeFromJson(json)
                if (backupData == null) {
                    _backupState.value = BackupOperationState.Error("Invalid backup file format")
                    return@launch
                }

                val result = backupRepository.restoreBackupData(backupData)
                if (result.isSuccess) {
                    _backupState.value = BackupOperationState.Success("Backup restored successfully")
                } else {
                    _backupState.value = BackupOperationState.Error("Restore failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _backupState.value = BackupOperationState.Error("Import failed: ${e.localizedMessage}")
            }
        }
    }
    
    private fun buildDateMillis(month: Int, day: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, 12) // Noon to avoid timezone edge cases
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

sealed class BackupOperationState {
    object Idle : BackupOperationState()
    object Loading : BackupOperationState()
    data class Success(val message: String) : BackupOperationState()
    data class Error(val message: String) : BackupOperationState()
}