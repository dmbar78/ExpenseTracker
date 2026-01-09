package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.TestTags
import androidx.navigation.NavController
import com.example.expensetracker.data.Category
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AddCategoryScreen(viewModel: ExpenseViewModel, navController: NavController, categoryName: String?) {
    var name by remember { mutableStateOf(categoryName ?: "") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.errorFlow.collectLatest { message ->
            errorMessage = message
            showErrorDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateBackFlow.collectLatest { 
            // Pass the new category name back to the previous screen
            navController.previousBackStackEntry?.savedStateHandle?.set("createdCategoryName", name)
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

    Column(modifier = Modifier.padding(16.dp).testTag(TestTags.ADD_CATEGORY_ROOT)) {
        Text("Add Category", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Category Name") },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.ADD_CATEGORY_NAME_FIELD)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val newCategory = Category(name = name)
                viewModel.insertCategory(newCategory)
            },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.ADD_CATEGORY_SAVE)
        ) {
            Text("Save")
        }
    }
}
