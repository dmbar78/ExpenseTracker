package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Account
import com.example.expensetracker.viewmodel.ExpenseViewModel
import com.example.expensetracker.viewmodel.ParsedTransfer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferAccountNotFoundScreen(
    viewModel: ExpenseViewModel,
    navController: NavController,
    sourceAccountName: String,
    destAccountName: String
) {
    val accounts by viewModel.allAccounts.collectAsState()
    var selectedSourceAccount by remember { mutableStateOf<Account?>(null) }
    var selectedDestAccount by remember { mutableStateOf<Account?>(null) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var destExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { navController.popBackStack() },
        title = { Text("Account Not Found") },
        text = {
            Column {
                if (accounts.find { it.name.equals(sourceAccountName, ignoreCase = true) } == null) {
                    Text("Couldn't find source account '$sourceAccountName' in database. Please select the required account from the list.")
                }
                ExposedDropdownMenuBox(expanded = sourceExpanded, onExpandedChange = { sourceExpanded = !sourceExpanded }) {
                    OutlinedTextField(
                        value = selectedSourceAccount?.name ?: sourceAccountName,
                        onValueChange = {},
                        label = { Text("Source Account") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedSourceAccount = account
                                    sourceExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (accounts.find { it.name.equals(destAccountName, ignoreCase = true) } == null) {
                    Text("Couldn't find destination account '$destAccountName' in database. Please select the required account from the list.")
                }
                ExposedDropdownMenuBox(expanded = destExpanded, onExpandedChange = { destExpanded = !destExpanded }) {
                    OutlinedTextField(
                        value = selectedDestAccount?.name ?: destAccountName,
                        onValueChange = {},
                        label = { Text("Destination Account") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = destExpanded, onDismissRequest = { destExpanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedDestAccount = account
                                    destExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val source = selectedSourceAccount?.name ?: sourceAccountName
                    val dest = selectedDestAccount?.name ?: destAccountName
                    (viewModel.pendingParsedData as? ParsedTransfer)?.let {
                        viewModel.reprocessTransfer(it.copy(sourceAccountName = source, destAccountName = dest))
                    }
                    navController.popBackStack()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = { navController.popBackStack() }) {
                Text("Cancel")
            }
        }
    )
}
