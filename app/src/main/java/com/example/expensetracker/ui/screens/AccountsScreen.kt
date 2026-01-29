package com.example.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Account
import com.example.expensetracker.viewmodel.ExpenseViewModel
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * State for the accounts total header.
 */
private sealed class AccountsTotalState {
    data object Loading : AccountsTotalState()
    data class Success(val total: BigDecimal, val currencyCode: String) : AccountsTotalState()
    data object RateMissing : AccountsTotalState()
}

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
    
    // State for accounts total
    var accountsTotal by remember { mutableStateOf<AccountsTotalState>(AccountsTotalState.Loading) }
    
    // Compute accounts total when accounts or default currency changes
    LaunchedEffect(accounts, defaultCurrency) {
        accountsTotal = AccountsTotalState.Loading
        
        if (accounts.isEmpty()) {
            accountsTotal = AccountsTotalState.Success(BigDecimal.ZERO, defaultCurrency)
        } else {
            var total = BigDecimal.ZERO
            var missingRate = false
            
            for (account in accounts) {
                if (account.currency == defaultCurrency) {
                    total = total.add(account.balance)
                } else {
                    val rate = viewModel.getAccountConversionRate(account.currency, defaultCurrency)
                    if (rate != null) {
                        total = total.add(account.balance.multiply(rate))
                    } else {
                        missingRate = true
                        break
                    }
                }
            }
            
            accountsTotal = if (missingRate) {
                AccountsTotalState.RateMissing
            } else {
                AccountsTotalState.Success(total, defaultCurrency)
            }
        }
    }

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
        Spacer(modifier = Modifier.height(8.dp))
        
        // Accounts total header
        AccountsTotalHeader(totalState = accountsTotal)
        
        Spacer(modifier = Modifier.height(8.dp))

        if (accounts.isEmpty()) {
            Text("No accounts yet. Tap 'Create New' to add one.")
        } else {
            LazyColumn {
                items(accounts) { account ->
                    AccountRow(
                        account = account,
                        defaultCurrency = defaultCurrency,
                        viewModel = viewModel,
                        onEditClick = { navController.navigate("editAccount/${account.id}") },
                        onAccountClick = { clickedAccount ->
                            viewModel.setExpenseIncomeAccountFilter(clickedAccount.name)
                            navController.navigate("home")
                        }
                    )
                }
            }
        }
    }
}

/**
 * Displays the total balance header above account list.
 */
@Composable
private fun AccountsTotalHeader(totalState: AccountsTotalState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        when (totalState) {
            is AccountsTotalState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                    strokeWidth = 2.dp
                )
            }
            is AccountsTotalState.Success -> {
                Text(
                    text = "Total: ${formatBalance(totalState.total)} ${totalState.currencyCode}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            is AccountsTotalState.RateMissing -> {
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

@Composable
private fun AccountRow(
    account: Account,
    defaultCurrency: String,
    viewModel: ExpenseViewModel,
    onEditClick: () -> Unit,
    onAccountClick: (Account) -> Unit
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
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onAccountClick(account) }
        ) {
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
