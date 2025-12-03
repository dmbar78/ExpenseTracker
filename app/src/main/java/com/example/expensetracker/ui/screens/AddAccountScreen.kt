package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Account
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(viewModel: ExpenseViewModel, navController: NavController) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var currencyCode by remember { mutableStateOf("") }
    var isCurrencyDropdownExpanded by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val currencies by viewModel.allCurrencies.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.errorFlow.collectLatest { message ->
            errorMessage = message
            showErrorDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateBackFlow.collectLatest { 
            navController.popBackStack()
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Add Account", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Account Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = balance,
            onValueChange = { balance = it },
            label = { Text("Balance") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = isCurrencyDropdownExpanded,
            onExpandedChange = { isCurrencyDropdownExpanded = !isCurrencyDropdownExpanded }
        ) {
            OutlinedTextField(
                value = currencies.find { it.code == currencyCode }?.name ?: "",
                onValueChange = {},
                label = { Text("Currency") },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCurrencyDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isCurrencyDropdownExpanded,
                onDismissRequest = { isCurrencyDropdownExpanded = false }
            ) {
                currencies.forEach { currencyItem ->
                    DropdownMenuItem(
                        text = { Text(currencyItem.name) },
                        onClick = {
                            currencyCode = currencyItem.code
                            isCurrencyDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val newAccount = Account(
                    name = name,
                    balance = balance.toDoubleOrNull() ?: 0.0,
                    currency = currencyCode
                )
                viewModel.insertAccount(newAccount)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}
