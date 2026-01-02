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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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
                items(accounts) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${it.name} (${it.currency})")
                            Text("Balance: ${formatBalance(it.balance)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { navController.navigate("editAccount/${it.id}") }) {
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }
}
