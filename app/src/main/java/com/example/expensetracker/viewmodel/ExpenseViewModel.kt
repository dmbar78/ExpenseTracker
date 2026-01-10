package com.example.expensetracker.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VoiceDateParse"
    }

    private val expenseRepository: ExpenseRepository
    private val accountRepository: AccountRepository
    private val categoryRepository: CategoryRepository
    private val currencyRepository: CurrencyRepository
    private val transferHistoryRepository: TransferHistoryRepository
    private val ledgerRepository: LedgerRepository

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    val allExpenses: StateFlow<List<Expense>>
    val allIncomes: StateFlow<List<Expense>>
    val allAccounts: StateFlow<List<Account>>
    val allCategories: StateFlow<List<Category>>
    val allCurrencies: StateFlow<List<Currency>>
    val allTransfers: StateFlow<List<TransferHistory>>

    // For voice recognition state
    private val _voiceRecognitionState = MutableStateFlow<VoiceRecognitionState>(VoiceRecognitionState.Idle)
    val voiceRecognitionState: StateFlow<VoiceRecognitionState> = _voiceRecognitionState

    // For general errors and navigation
    private val _errorChannel = Channel<String>()
    val errorFlow = _errorChannel.receiveAsFlow()

    private val _navigateBackChannel = Channel<Unit>()
    val navigateBackFlow = _navigateBackChannel.receiveAsFlow()

    private val _navigateTo = Channel<String>()
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

    var pendingParsedData: Any? = null

    init {
        val database = AppDatabase.getDatabase(application)
        expenseRepository = ExpenseRepository(database.expenseDao())
        accountRepository = AccountRepository(database.accountDao())
        categoryRepository = CategoryRepository(database.categoryDao())
        currencyRepository = CurrencyRepository(database.currencyDao())
        transferHistoryRepository = TransferHistoryRepository(database.transferHistoryDao())
        ledgerRepository = LedgerRepository(database.ledgerDao())

        allExpenses = expenseRepository.getExpensesByType("Expense").stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allIncomes = expenseRepository.getExpensesByType("Income").stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allAccounts = accountRepository.allAccounts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allCategories = categoryRepository.allCategories.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allCurrencies = currencyRepository.allCurrencies.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        allTransfers = transferHistoryRepository.allTransfers.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
            
            _voiceRecognitionState.value = VoiceRecognitionState.RecognitionFailed("Couldn't recognize input: '$spokenText'. Please repeat in the format: Expense from/Income to <Account> <Amount> Category <Category> or Transfer from <Source Account> to <Destination Account> <Amount>")
        }
    }

    private suspend fun processParsedTransfer(parsedTransfer: ParsedTransfer) {
        val accounts = allAccounts.first { it.isNotEmpty() }
        
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
        val restAfterTo = input.substring(toIndex + 4).trim()

        // 1) Parse (and remove) trailing date first so day numbers (e.g., "1st") don't get treated as amount
        val (restWithoutDate, transferDate) = parseTrailingSpokenDate(restAfterTo)

        // Regex that handles both dot and comma decimal separators, and thousand separators
        val amountRegex = Regex("([\\d,]+\\.?\\d*|[\\d.]+,?\\d*)")
        val amountMatch = amountRegex.findAll(restWithoutDate).lastOrNull() ?: return null
        val amount = parseMoneyAmount(amountMatch.value) ?: return null

        val destAccountStr = restWithoutDate.substring(0, amountMatch.range.first).trim()

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
        val amountStr = amountMatch.value

        val amount = parseMoneyAmount(amountStr)
        if (amount == null) {
            return null
        }

        // Parse optional trailing date from category string
        val (finalCategoryStr, parsedDate) = parseTrailingSpokenDate(categoryStr)
        val expenseDate = parsedDate ?: System.currentTimeMillis()

        return ParsedExpense(accountName = accountStr, amount = amount, categoryName = finalCategoryStr, type = type, expenseDate = expenseDate)
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
        accountRepository.delete(account)
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

    fun updateExpense(expense: Expense) = viewModelScope.launch {
        try {
            ledgerRepository.updateExpense(expense)
            _navigateBackChannel.send(Unit)
        } catch (e: IllegalStateException) {
            _errorChannel.send(e.message ?: "Failed to update expense.")
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

    // Data-only version used internally (e.g. voice transfer) - no navigation
    private suspend fun updateAccountInternal(account: Account): Boolean {
        if (account.name.isBlank() || account.currency.isBlank()) {
            _errorChannel.send("Account Name and Currency cannot be empty.")
            return false
        }
        return try {
            accountRepository.update(account)
            true
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _errorChannel.send("Account with name '${account.name}' already exists.")
            false
        } catch (e: Exception) {
            _errorChannel.send("An unknown error occurred.")
            false
        }
    }

    fun insertAccount(account: Account) = viewModelScope.launch {
        if (account.name.isBlank() || account.currency.isBlank()) {
            _errorChannel.send("Account Name and Currency cannot be empty.")
            return@launch
        }
        try {
            accountRepository.insert(account)
            _navigateBackChannel.send(Unit)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _errorChannel.send("Account with name '${account.name}' already exists.")
        } catch (e: Exception) {
            _errorChannel.send("An unknown error occurred.")
        }
    }

    fun updateAccount(account: Account) = viewModelScope.launch {
        if (updateAccountInternal(account)) {
            _navigateBackChannel.send(Unit)
        }
    }

    fun insertCategory(category: Category) = viewModelScope.launch {
        if (category.name.isBlank()) {
            _errorChannel.send("Category Name cannot be empty.")
            return@launch
        }
        try {
            categoryRepository.insert(category)
            _navigateBackChannel.send(Unit)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _errorChannel.send("Category with name '${category.name}' already exists.")
        } catch (e: Exception) {
            _errorChannel.send("An unknown error occurred.")
        }
    }

    fun updateCategory(category: Category) = viewModelScope.launch {
        if (category.name.isBlank()) {
            _errorChannel.send("Category Name cannot be empty.")
            return@launch
        }
        try {
            categoryRepository.update(category)
            _navigateBackChannel.send(Unit)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _errorChannel.send("Category with name '${category.name}' already exists.")
        } catch (e: Exception) {
            _errorChannel.send("An unknown error occurred.")
        }
    }

    fun insertCurrency(currency: Currency) = viewModelScope.launch {
        if (currency.code.isBlank() || currency.name.isBlank()) {
            _errorChannel.send("Code and Name cannot be empty.")
            return@launch
        }
        try {
            currencyRepository.insert(currency)
            _navigateBackChannel.send(Unit)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _errorChannel.send("Currency with code '${currency.code}' already exists.")
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

    /**
     * Builds epoch millis for a given month (0-based) and day in the current year.
     */
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