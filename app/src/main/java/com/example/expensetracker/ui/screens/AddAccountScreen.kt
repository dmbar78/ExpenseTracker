package com.example.expensetracker.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.example.expensetracker.ui.screens.content.AddAccountCallbacks
import com.example.expensetracker.ui.screens.content.AddAccountScreenContent
import com.example.expensetracker.ui.screens.content.AddAccountState
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * Thin wrapper for AddAccountScreen.
 * - Collects errorFlow and navigateBackFlow from ViewModel
 * - Handles savedStateHandle result passing for createdAccountName
 * - Delegates all UI rendering to AddAccountScreenContent
 */
@Composable
fun AddAccountScreen(viewModel: ExpenseViewModel, navController: NavController) {
    // Track the current account name for savedStateHandle result passing
    var currentName by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val currencies by viewModel.allCurrencies.collectAsState()

    // Collect error flow and show error dialog
    LaunchedEffect(Unit) {
        viewModel.errorFlow.collectLatest { message ->
            errorMessage = message
            showErrorDialog = true
        }
    }

    // Collect navigate back signal and pass result via savedStateHandle
    LaunchedEffect(Unit) {
        viewModel.navigateBackFlow.collectLatest { 
            // Pass the new account name back to the previous screen
            navController.previousBackStackEntry?.savedStateHandle?.set("createdAccountName", currentName)
            navController.popBackStack()
        }
    }

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
        state = AddAccountState(name = currentName),
        currencies = currencies,
        callbacks = AddAccountCallbacks(
            onNameChange = { currentName = it },
            onBalanceChange = { /* Balance tracked locally in Content */ },
            onCurrencySelect = { /* Currency tracked locally in Content */ },
            onSave = { account -> viewModel.insertAccount(account) }
        )
    )
}
