package com.example.expensetracker.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.example.expensetracker.ui.screens.content.AddAccountCallbacks
import com.example.expensetracker.ui.screens.content.AddAccountScreenContent
import com.example.expensetracker.ui.screens.content.AddAccountState
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch

/**
 * Thin wrapper for AddAccountScreen.
 * - Handles account creation with local navigation (not using shared navigateBackFlow)
 * - Shows error dialog on failure
 * - Delegates all UI rendering to AddAccountScreenContent
 */
@Composable
fun AddAccountScreen(
    viewModel: ExpenseViewModel,
    navController: NavController,
    accountName: String? = null,
    currencyCode: String? = null
) {
    // Track the current account name for savedStateHandle result passing
    var currentName by remember { mutableStateOf(accountName ?: "") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val currencies by viewModel.allCurrencies.collectAsState()
    val defaultCurrency by viewModel.defaultCurrencyCode.collectAsState("EUR")
    val scope = rememberCoroutineScope()

    // Error dialog (side-effect owned by wrapper)
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

    // Delegate UI to Content composable
    AddAccountScreenContent(
        state = AddAccountState(
            name = currentName,
            currencyCode = currencyCode ?: defaultCurrency
        ),
        currencies = currencies,
        callbacks = AddAccountCallbacks(
            onNameChange = { currentName = it },
            onBalanceChange = { /* Balance tracked locally in Content */ },
            onCurrencySelect = { /* Currency tracked locally in Content */ },
            onSave = { account ->
                if (isSaving) return@AddAccountCallbacks
                isSaving = true
                
                scope.launch {
                    val result = viewModel.insertAccountAndReturn(account)
                    // Switch to Main thread for UI operations
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        result.fold(
                            onSuccess = {
                                // Pass the new account name back to the previous screen (trimmed)
                                navController.previousBackStackEntry?.savedStateHandle?.set("createdAccountName", currentName.trim())
                                navController.popBackStack()
                            },
                            onFailure = { error ->
                                errorMessage = error.message ?: "An unknown error occurred."
                                showErrorDialog = true
                                isSaving = false
                            }
                        )
                    }
                }
            }
        )
    )
}

