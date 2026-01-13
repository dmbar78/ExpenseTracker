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
    val tabs = listOf("Expense", "Income", "Transfers")
    
    // Filter state and data
    val filterState by viewModel.filterState.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    
    // Filtered lists
    val filteredExpenses by viewModel.filteredExpenses.collectAsState()
    val filteredIncomes by viewModel.filteredIncomes.collectAsState()
    val filteredTransfers by viewModel.filteredTransfers.collectAsState()
    
    // Default currency for totals
    val defaultCurrency by viewModel.defaultCurrencyCode.collectAsState()
    
    // Totals state
    var expensesTotal by remember { mutableStateOf<TotalState>(TotalState.Loading) }
    var incomesTotal by remember { mutableStateOf<TotalState>(TotalState.Loading) }
    var transfersTotal by remember { mutableStateOf<TotalState>(TotalState.Loading) }
    
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Filter chips row (above tabs)
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
                onClearTimeFilter = { viewModel.resetTimeFilter() },
                onClearAccountFilter = { viewModel.resetExpenseIncomeAccountFilter() },
                onClearCategoryFilter = { viewModel.resetCategoryFilter() },
                onClearTransferFilter = { viewModel.resetTransferFilters() }
            )
            
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTabIndex == index,
                        onClick = { viewModel.onTabSelected(index) },
                        text = { Text(text = title) })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTabIndex) {
                0 -> {
                    TotalHeader(totalState = expensesTotal)
                    TransactionList(filteredExpenses, navController)
                }
                1 -> {
                    TotalHeader(totalState = incomesTotal)
                    TransactionList(filteredIncomes, navController)
                }
                2 -> {
                    TotalHeader(totalState = transfersTotal)
                    TransfersTab(filteredTransfers, navController)
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
}

/**
 * Displays the total amount header above transaction lists.
 */
@Composable
private fun TotalHeader(totalState: TotalState) {
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
    val groupedTransactions = transactions.groupBy { 
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(it.expenseDate)
    }

    LazyColumn {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransfersTab(transfers: List<TransferHistory>, navController: NavController) {
    val groupedTransfers = transfers.groupBy {
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(it.date)
    }

    LazyColumn {
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
