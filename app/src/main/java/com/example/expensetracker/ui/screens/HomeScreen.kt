package com.example.expensetracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.TransferHistory
import com.example.expensetracker.viewmodel.ExpenseViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: ExpenseViewModel, navController: NavController) {
    val selectedTabIndex by viewModel.selectedTab.collectAsState()
    val tabs = listOf("Expense", "Income", "Transfers")

    Column(modifier = Modifier.padding(16.dp)) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTabIndex == index,
                    onClick = { viewModel.onTabSelected(index) },
                    text = { Text(text = title) })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTabIndex) {
            0 -> TransactionList(viewModel.allExpenses.collectAsState().value, navController)
            1 -> TransactionList(viewModel.allIncomes.collectAsState().value, navController)
            2 -> TransfersTab(viewModel, navController)
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
private fun TransfersTab(viewModel: ExpenseViewModel, navController: NavController) {
    val transfers by viewModel.allTransfers.collectAsState()
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
