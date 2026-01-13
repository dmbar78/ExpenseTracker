package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Account
import com.example.expensetracker.viewmodel.ExpenseViewModel
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats a BigDecimal for display with exactly 2 decimal places.
 */
private fun formatBalance(balance: BigDecimal): String {
    return balance.setScale(2, RoundingMode.HALF_UP).toPlainString()
}

@Composable
fun AccountsScreen(viewModel: ExpenseViewModel, navController: NavController) {
    val accounts by viewModel.allAccounts.collectAsState()
    val defaultCurrency by viewModel.defaultCurrencyCode.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Accounts", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { navController.navigate("addAccount") }) {
                Text("Create New")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (accounts.isEmpty()) {
            Text("No accounts yet. Tap 'Create New' to add one.")
        } else {
            LazyColumn {
                items(accounts) { account ->
                    AccountRow(
                        account = account,
                        defaultCurrency = defaultCurrency,
                        viewModel = viewModel,
                        onEditClick = { navController.navigate("editAccount/${account.id}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    defaultCurrency: String,
    viewModel: ExpenseViewModel,
    onEditClick: () -> Unit
) {
    // State for converted balance
    var convertedBalance by remember { mutableStateOf<BigDecimal?>(null) }
    
    // Calculate converted balance when account or default currency changes
    LaunchedEffect(account, defaultCurrency) {
        if (account.currency != defaultCurrency) {
            val rate = viewModel.getAccountConversionRate(account.currency, defaultCurrency)
            convertedBalance = rate?.let { account.balance.multiply(it) }
        } else {
            convertedBalance = null // No conversion needed
        }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${account.name} (${account.currency})")
            
            // Show balance with optional converted value
            val balanceText = buildString {
                append("Balance: ${formatBalance(account.balance)}")
                if (account.currency != defaultCurrency) {
                    val converted = convertedBalance
                    if (converted != null) {
                        append(" (${formatBalance(converted)} $defaultCurrency)")
                    } else {
                        append(" (— $defaultCurrency)")
                    }
                }
            }
            Text(balanceText, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onEditClick) {
            Text("Edit")
        }
    }
}
