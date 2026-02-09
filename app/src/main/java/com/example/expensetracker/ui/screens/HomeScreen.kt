package com.example.expensetracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.TimeFilter
import com.example.expensetracker.data.TransferHistory
import com.example.expensetracker.data.getWeekStartMillis
import com.example.expensetracker.ui.components.*
import com.example.expensetracker.viewmodel.ExpenseViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.expensetracker.viewmodel.SortOption
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

/**
 * Represents the state of a total calculation.
 */
sealed class TotalState {
    data object Loading : TotalState()
    data class Success(val total: BigDecimal, val currencyCode: String) : TotalState()
    data object RateMissing : TotalState()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: ExpenseViewModel, navController: NavController) {
    val selectedTabIndex by viewModel.selectedTab.collectAsState()
    val tabs = listOf("Expense", "Income", "Transfers", "Debts")
    
    // Filter state and data
    val filterState by viewModel.filterState.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    
    // Filtered lists for main tabs
    val filteredExpenses by viewModel.filteredExpenses.collectAsState()
    val filteredIncomes by viewModel.filteredIncomes.collectAsState()
    val filteredTransfers by viewModel.filteredTransfers.collectAsState()
    
    // Data for Debts tab
    val allDebts by viewModel.allDebts.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allIncomes by viewModel.allIncomes.collectAsState()
    
    // Default currency for totals
    val defaultCurrency by viewModel.defaultCurrencyCode.collectAsState()
    
    // Totals state
    var expensesTotal by remember { mutableStateOf<TotalState>(TotalState.Loading) }
    var incomesTotal by remember { mutableStateOf<TotalState>(TotalState.Loading) }
    var transfersTotal by remember { mutableStateOf<TotalState>(TotalState.Loading) }

    // Sort state
    val expenseSortOption by viewModel.expenseSortOption.collectAsState()
    val incomeSortOption by viewModel.incomeSortOption.collectAsState()
    
    // Calculate totals when data or default currency changes
    LaunchedEffect(filteredExpenses, defaultCurrency) {
        expensesTotal = TotalState.Loading
        val total = viewModel.calculateExpensesTotal(filteredExpenses, defaultCurrency)
        expensesTotal = if (total != null) {
            TotalState.Success(total, defaultCurrency)
        } else {
            TotalState.RateMissing
        }
    }
    
    LaunchedEffect(filteredIncomes, defaultCurrency) {
        incomesTotal = TotalState.Loading
        val total = viewModel.calculateExpensesTotal(filteredIncomes, defaultCurrency)
        incomesTotal = if (total != null) {
            TotalState.Success(total, defaultCurrency)
        } else {
            TotalState.RateMissing
        }
    }
    
    LaunchedEffect(filteredTransfers, defaultCurrency) {
        transfersTotal = TotalState.Loading
        val total = viewModel.calculateTransfersTotal(filteredTransfers, defaultCurrency)
        transfersTotal = if (total != null) {
            TotalState.Success(total, defaultCurrency)
        } else {
            TotalState.RateMissing
        }
    }
    
    // Dialog states
    var showMainFilterMenu by remember { mutableStateOf(false) }
    var showTimeFilterMenu by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showYearPicker by remember { mutableStateOf(false) }
    var showPeriodPicker by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showTextQueryDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            // Filter chips row (above tabs) (Hide for Debts tab logic if desired, or keep globally)
            // Keeping globally for now, though arguably filters might not apply to Debts in this simplistic implementation
            FilterChipsRow(
                filterState = filterState,
                onTimeFilterClick = {
                    // Open the appropriate picker based on current time filter type
                    when (filterState.timeFilter) {
                        is TimeFilter.Day -> showDayPicker = true
                        is TimeFilter.Week -> showWeekPicker = true
                        is TimeFilter.Month -> showMonthPicker = true
                        is TimeFilter.Year -> showYearPicker = true
                        is TimeFilter.Period -> showPeriodPicker = true
                        is TimeFilter.AllTime -> showPeriodPicker = true
                        is TimeFilter.None -> showTimeFilterMenu = true
                    }
                },
                onAccountFilterClick = { showAccountDialog = true },
                onCategoryFilterClick = { showCategoryDialog = true },
                onTransferFilterClick = { showTransferDialog = true },
                onTextQueryFilterClick = { showTextQueryDialog = true },
                onClearTimeFilter = { viewModel.resetTimeFilter() },
                onClearAccountFilter = { viewModel.resetExpenseIncomeAccountFilter() },
                onClearCategoryFilter = { viewModel.resetCategoryFilter() },
                onClearTransferFilter = { viewModel.resetTransferFilters() },
                onClearTextQueryFilter = { viewModel.resetTextQueryFilter() }
            )
            
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTabIndex == index,
                        onClick = { viewModel.onTabSelected(index) },
                        text = { 
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTabIndex) {
                0 -> {
                    TotalHeaderWithSort(
                        totalState = expensesTotal,
                        sortOption = expenseSortOption,
                        onSortChange = { viewModel.setExpenseSortOption(it) }
                    )
                    TransactionList(filteredExpenses, navController)
                }
                1 -> {
                    TotalHeaderWithSort(
                        totalState = incomesTotal,
                        sortOption = incomeSortOption,
                        onSortChange = { viewModel.setIncomeSortOption(it) }
                    )
                    TransactionList(filteredIncomes, navController)
                }
                2 -> {
                    TotalHeader(totalState = transfersTotal)
                    TransfersTab(filteredTransfers, navController)
                }
                3 -> {
                    // No total header for Debts for now (or could calculate net debt)
                    DebtsTab(allDebts, allExpenses, allIncomes, filteredExpenses, filteredIncomes, navController)
                }
            }
        }
        
        // Filter icon button (bottom-left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            FilterIconButton(
                hasActiveFilters = filterState.hasActiveFilters(),
                onClick = { showMainFilterMenu = true }
            )
            
            // Main filter menu
            FilterMainMenu(
                expanded = showMainFilterMenu,
                onDismiss = { showMainFilterMenu = false },
                onTimeClick = { showTimeFilterMenu = true },
                onAccountClick = { showAccountDialog = true },
                onTransferClick = { showTransferDialog = true },
                onCategoryClick = { showCategoryDialog = true },
                onTextQueryClick = { showTextQueryDialog = true },
                onResetAll = { viewModel.resetAllFilters() }
            )
            
            // Time filter submenu
            TimeFilterMenu(
                expanded = showTimeFilterMenu,
                onDismiss = { showTimeFilterMenu = false },
                onDayClick = { showDayPicker = true },
                onWeekClick = { showWeekPicker = true },
                onMonthClick = { showMonthPicker = true },
                onYearClick = { showYearPicker = true },
                onPeriodClick = { showPeriodPicker = true }
            )
        }
    }
    
    // Day picker dialog
    if (showDayPicker) {
        val currentDay = (filterState.timeFilter as? TimeFilter.Day)?.dateMillis
        DayPickerDialog(
            currentSelection = currentDay,
            onConfirm = { millis ->
                viewModel.setTimeFilter(TimeFilter.Day(millis))
                showDayPicker = false
            },
            onReset = {
                viewModel.resetTimeFilter()
                showDayPicker = false
            },
            onCancel = { showDayPicker = false }
        )
    }
    
    // Week picker dialog
    if (showWeekPicker) {
        val currentWeek = (filterState.timeFilter as? TimeFilter.Week)?.weekStartMillis
        WeekPickerDialog(
            currentSelection = currentWeek,
            onConfirm = { millis ->
                viewModel.setTimeFilter(TimeFilter.Week(millis))
                showWeekPicker = false
            },
            onReset = {
                viewModel.resetTimeFilter()
                showWeekPicker = false
            },
            onCancel = { showWeekPicker = false }
        )
    }
    
    // Month picker dialog
    if (showMonthPicker) {
        val currentMonth = filterState.timeFilter as? TimeFilter.Month
        MonthPickerDialog(
            currentYear = currentMonth?.year,
            currentMonth = currentMonth?.month,
            onConfirm = { year, month ->
                viewModel.setTimeFilter(TimeFilter.Month(year, month))
                showMonthPicker = false
            },
            onReset = {
                viewModel.resetTimeFilter()
                showMonthPicker = false
            },
            onCancel = { showMonthPicker = false }
        )
    }
    
    // Year picker dialog
    if (showYearPicker) {
        val currentYear = (filterState.timeFilter as? TimeFilter.Year)?.year
        YearPickerDialog(
            currentYear = currentYear,
            onConfirm = { year ->
                viewModel.setTimeFilter(TimeFilter.Year(year))
                showYearPicker = false
            },
            onReset = {
                viewModel.resetTimeFilter()
                showYearPicker = false
            },
            onCancel = { showYearPicker = false }
        )
    }
    
    // Period picker dialog
    if (showPeriodPicker) {
        val currentPeriod = filterState.timeFilter as? TimeFilter.Period
        PeriodPickerDialog(
            currentStartMillis = currentPeriod?.startMillis,
            currentEndMillis = currentPeriod?.endMillis,
            onConfirm = { start, end ->
                viewModel.setTimeFilter(TimeFilter.Period(start, end))
                showPeriodPicker = false
            },
            onAllTime = {
                viewModel.setTimeFilter(TimeFilter.AllTime)
                showPeriodPicker = false
            },
            onReset = {
                viewModel.resetTimeFilter()
                showPeriodPicker = false
            },
            onCancel = { showPeriodPicker = false }
        )
    }
    
    // Account filter dialog
    if (showAccountDialog) {
        AccountFilterDialog(
            title = "Filter by Account",
            accounts = accounts,
            currentSelection = filterState.expenseIncomeAccount,
            onConfirm = { account ->
                viewModel.setExpenseIncomeAccountFilter(account)
                showAccountDialog = false
            },
            onReset = {
                viewModel.resetExpenseIncomeAccountFilter()
                showAccountDialog = false
            },
            onCancel = { showAccountDialog = false }
        )
    }
    
    // Transfer filter dialog
    if (showTransferDialog) {
        TransferFilterDialog(
            accounts = accounts,
            currentSourceAccount = filterState.transferSourceAccount,
            currentDestAccount = filterState.transferDestAccount,
            onConfirm = { source, dest ->
                viewModel.setTransferAccountFilters(source, dest)
                showTransferDialog = false
            },
            onReset = {
                viewModel.resetTransferFilters()
                showTransferDialog = false
            },
            onCancel = { showTransferDialog = false }
        )
    }
    
    // Category filter dialog
    if (showCategoryDialog) {
        CategoryFilterDialog(
            categories = categories,
            currentSelection = filterState.category,
            onConfirm = { category ->
                viewModel.setCategoryFilter(category)
                showCategoryDialog = false
            },
            onReset = {
                viewModel.resetCategoryFilter()
                showCategoryDialog = false
            },
            onCancel = { showCategoryDialog = false }
        )
    }
    
    // Text query filter dialog
    if (showTextQueryDialog) {
        TextQueryFilterDialog(
            currentQuery = filterState.textQuery,
            onConfirm = { query ->
                viewModel.setTextQueryFilter(query)
                showTextQueryDialog = false
            },
            onReset = {
                viewModel.resetTextQueryFilter()
                showTextQueryDialog = false
            },
            onCancel = { showTextQueryDialog = false }
        )
    }
}

@Composable
private fun TotalHeaderWithSort(
    totalState: TotalState,
    sortOption: SortOption,
    onSortChange: (SortOption) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Center: Total Amount
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            when (totalState) {
                is TotalState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(4.dp),
                        strokeWidth = 2.dp
                    )
                }
                is TotalState.Success -> {
                    Text(
                        text = "Total: ${formatMoney(totalState.total)} ${totalState.currencyCode}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
                is TotalState.RateMissing -> {
                    Text(
                        text = "Total unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Right: Sort Button
        Box(
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            var expanded by remember { mutableStateOf(false) }

            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.displayName,
                                fontWeight = if (option == sortOption) androidx.compose.ui.text.font.FontWeight.Bold else null
                            )
                        },
                        onClick = {
                            onSortChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Displays the total amount header above transaction lists.
 */
@Composable
private fun TotalHeader(totalState: TotalState) {
   // Reused or deprecated depending on if Transfers need sort. 
   // Transfers currently don't have sort requirements in prompt, so keeping this for them if needed, 
   // or just using the new one without sort options if we wanted to enforce consistency.
   // But Transfers tab helper calls TotalHeader(transfersTotal).
   // I'll keep this simple implementation for cases without sort.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        when (totalState) {
            is TotalState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                    strokeWidth = 2.dp
                )
            }
            is TotalState.Success -> {
                Text(
                    text = "Total: ${formatMoney(totalState.total)} ${totalState.currencyCode}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            is TotalState.RateMissing -> {
                Text(
                    text = "Total unavailable (missing rates)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Formats a BigDecimal for display with exactly 2 decimal places.
 */
private fun formatMoney(amount: BigDecimal): String {
    return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionList(transactions: List<Expense>, navController: NavController) {
    // Use bespoke sequential grouping to preserve sort order (e.g. By Amount)
    // while still grouping consecutive items with the same date header.
    val groupedTransactions = remember(transactions) {
        groupSequentially(transactions) {
            SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(it.expenseDate)
        }
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
    ) {
        groupedTransactions.forEach { (date, transactionsOnDate) ->
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp)
                ) {
                    Text(date, style = MaterialTheme.typography.titleMedium)
                }
            }

            items(transactionsOnDate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .clickable { navController.navigate("editExpense/${it.id}?type=${it.type}") }
                ) {
                    Text(it.account, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text(formatMoney(it.amount), modifier = Modifier.weight(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text(it.currency, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text(it.category, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Groups items sequentially.
 * If consecutive items have the same key, they are added to the same group.
 * If the key changes, a new group is started.
 * This preserves the original order of items (e.g. when sorted by Amount).
 */
private fun <T, K> groupSequentially(items: List<T>, keySelector: (T) -> K): List<Pair<K, List<T>>> {
    if (items.isEmpty()) return emptyList()
    val result = mutableListOf<Pair<K, MutableList<T>>>()
    var currentKey = keySelector(items.first())
    var currentList = mutableListOf(items.first())
    
    for (i in 1 until items.size) {
        val item = items[i]
        val key = keySelector(item)
        if (key == currentKey) {
            currentList.add(item)
        } else {
            result.add(currentKey to currentList)
            currentKey = key
            currentList = mutableListOf(item)
        }
    }
    result.add(currentKey to currentList)
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransfersTab(transfers: List<TransferHistory>, navController: NavController) {
    val groupedTransfers = transfers.groupBy {
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(it.date)
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
    ) {
        groupedTransfers.forEach { (date, transfersOnDate) ->
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp)
                ) {
                    Text(date, style = MaterialTheme.typography.titleMedium)
                }
            }

            items(transfersOnDate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .clickable { navController.navigate("editTransfer/${it.id}") }
                ) {
                    Text(it.sourceAccount, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text(it.destinationAccount, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text(formatMoney(it.amount), modifier = Modifier.weight(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text(it.currency, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DebtsTab(
    debts: List<com.example.expensetracker.data.Debt>, 
    allExpenses: List<Expense>,
    allIncomes: List<Expense>,
    filteredExpenses: List<Expense>,
    filteredIncomes: List<Expense>,
    navController: NavController
) {
    // Helper to find parent expense
    fun findParent(debt: com.example.expensetracker.data.Debt): Expense? {
        return allExpenses.find { it.id == debt.parentExpenseId } 
            ?: allIncomes.find { it.id == debt.parentExpenseId }
    }
    
    // Filter Logic: Only show debts where parent is in currently filtered lists
    // This allows Time/Account filters to apply to Debts tab based on parent creation
    val filteredParentIds = remember(filteredExpenses, filteredIncomes) {
        (filteredExpenses.map { it.id } + filteredIncomes.map { it.id }).toSet()
    }
    
    val validDebts = debts.mapNotNull { debt -> 
        findParent(debt)?.let { parent ->
             if (parent.id in filteredParentIds) debt to parent else null
        }
    }
    
    val groupedDebts = validDebts.groupBy { (_, parent) ->
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(parent.expenseDate)
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
    ) {
        // Handle empty
        if (groupedDebts.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No debts found matching filters", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        
        groupedDebts.forEach { (date, items) ->
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp)
                ) {
                    Text(date, style = MaterialTheme.typography.titleMedium)
                }
            }

            items(items) { (debt, parent) ->
                val isClosed = debt.status == "CLOSED"
                val textDecoration = if (isClosed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                val textColor = if (isClosed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .clickable { navController.navigate("editExpense/${parent.id}?type=${parent.type}") },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Account
                    Text(
                        text = parent.account,
                        modifier = Modifier.weight(0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = textDecoration,
                        color = textColor
                    )

                    // Description / Notes
                    Column(modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp)) {
                        Text(
                            text = parent.comment ?: parent.category,
                            style = MaterialTheme.typography.bodyMedium, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = textDecoration,
                            color = textColor
                        )
                        if (!debt.notes.isNullOrBlank()) {
                            Text(debt.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    
                    // Amount and Status
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${formatMoney(parent.amount)} ${parent.currency}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            textDecoration = textDecoration,
                            color = textColor
                        )
                        Text(
                            text = debt.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (debt.status == "OPEN") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
