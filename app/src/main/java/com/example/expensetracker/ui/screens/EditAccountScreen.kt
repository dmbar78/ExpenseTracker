package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAccountScreen(accountId: Int, viewModel: ExpenseViewModel, navController: NavController) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(accountId) {
        viewModel.loadAccount(accountId)
    }

    val account by viewModel.selectedAccount.collectAsState()
    val currencies by viewModel.allCurrencies.collectAsState()

    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var currencyCode by remember { mutableStateOf("") }
    var isCurrencyDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(account) {
        account?.let {
            name = it.name
            balance = it.balance.toString()
            currencyCode = it.currency
        }
    }

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
        Text("Edit Account", style = MaterialTheme.typography.headlineSmall)
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
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    account?.let {
                        val updatedAccount = it.copy(
                            name = name,
                            balance = balance.toDoubleOrNull() ?: it.balance,
                            currency = currencyCode
                        )
                        viewModel.updateAccount(updatedAccount)
                    }
                },
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text("Save")
            }
            Button(
                onClick = { showDeleteDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Text("Delete")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete this account?") },
            confirmButton = {
                Button(
                    onClick = {
                        account?.let { viewModel.deleteAccount(it) }
                        showDeleteDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}
