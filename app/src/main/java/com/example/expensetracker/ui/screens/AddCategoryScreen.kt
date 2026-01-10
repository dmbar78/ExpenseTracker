package com.example.expensetracker.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.example.expensetracker.ui.screens.content.AddCategoryCallbacks
import com.example.expensetracker.ui.screens.content.AddCategoryScreenContent
import com.example.expensetracker.ui.screens.content.AddCategoryState
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * Thin wrapper for AddCategoryScreen.
 * - Collects errorFlow and navigateBackFlow from ViewModel
 * - Handles savedStateHandle result passing for createdCategoryName
 * - Delegates all UI rendering to AddCategoryScreenContent
 */
@Composable
fun AddCategoryScreen(viewModel: ExpenseViewModel, navController: NavController, categoryName: String?) {
    // Track the current category name for savedStateHandle result passing
    var currentName by remember { mutableStateOf(categoryName ?: "") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

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
            // Pass the new category name back to the previous screen
            navController.previousBackStackEntry?.savedStateHandle?.set("createdCategoryName", currentName)
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
    AddCategoryScreenContent(
        state = AddCategoryState(categoryName = currentName),
        callbacks = AddCategoryCallbacks(
            onNameChange = { currentName = it },
            onSave = { category -> viewModel.insertCategory(category) }
        )
    )
}
