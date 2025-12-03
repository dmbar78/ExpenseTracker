package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Currency
import com.example.expensetracker.viewmodel.ExpenseViewModel

@Composable
fun CurrenciesScreen(viewModel: ExpenseViewModel, navController: NavController) {
    val currencies by viewModel.allCurrencies.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var currencyToDelete by remember { mutableStateOf<Currency?>(null) }

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
                items(currencies) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${it.name} (${it.code})")
                        Button(
                            onClick = { 
                                currencyToDelete = it
                                showDialog = true 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete Currency") },
            text = { Text("Are you sure you want to delete this currency?") },
            confirmButton = {
                Button(
                    onClick = {
                        currencyToDelete?.let { viewModel.deleteCurrency(it) }
                        showDialog = false
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = { showDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}
