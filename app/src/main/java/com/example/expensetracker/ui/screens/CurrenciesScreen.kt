package com.example.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Currency
import com.example.expensetracker.viewmodel.ExpenseViewModel

@Composable
fun CurrenciesScreen(viewModel: ExpenseViewModel, navController: NavController) {
    val currencies by viewModel.allCurrencies.collectAsState()
    val defaultCurrencyCode by viewModel.defaultCurrencyCode.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Currencies", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { navController.navigate("addCurrency") }) {
                Text("Create New")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (currencies.isEmpty()) {
            Text("No currencies yet. Tap 'Create New' to add one.")
        } else {
            LazyColumn {
                items(currencies) { currency ->
                    CurrencyRow(
                        currency = currency,
                        isDefault = currency.code == defaultCurrencyCode,
                        onClick = { navController.navigate("editCurrency/${currency.id}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyRow(
    currency: Currency,
    isDefault: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${currency.name} (${currency.code})",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (isDefault) {
                    Text(
                        text = "Default",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
