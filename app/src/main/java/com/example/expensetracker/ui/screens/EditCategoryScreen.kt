package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.navigation.NavController
import com.example.expensetracker.data.Category
import com.example.expensetracker.ui.TestTags
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditCategoryScreen(categoryId: Int, viewModel: ExpenseViewModel, navController: NavController) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showCannotDeleteDefaultDialog by remember { mutableStateOf(false) }

    LaunchedEffect(categoryId) {
        viewModel.loadCategory(categoryId)
    }

    val category by viewModel.selectedCategory.collectAsState()
    val isDefaultCategory = category?.name.equals("Default", ignoreCase = true)

    // Helper state for the content to initialize with loaded data
    val currentCategory = category

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
    
    if (showCannotDeleteDefaultDialog) {
        AlertDialog(
            onDismissRequest = { showCannotDeleteDefaultDialog = false },
            title = { Text("Cannot Delete") },
            text = { Text("The 'Default' category cannot be deleted.") },
            confirmButton = {
                Button(onClick = { showCannotDeleteDefaultDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Category") },
            text = { Text("Are you sure you want to delete this category?") },
            confirmButton = {
                Button(
                    onClick = {
                        category?.let { viewModel.deleteCategory(it) }
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

    EditCategoryScreenContent(
        category = currentCategory,
        isDefaultCategory = isDefaultCategory ?: false,
        onSave = { newName ->
            currentCategory?.let {
                val updatedCategory = it.copy(name = newName)
                viewModel.updateCategory(updatedCategory)
            }
        },
        onDeleteRequest = {
            if (isDefaultCategory == true) {
                showCannotDeleteDefaultDialog = true
            } else {
                showDeleteDialog = true
            }
        }
    )
}

@Composable
fun EditCategoryScreenContent(
    category: Category?,
    isDefaultCategory: Boolean,
    onSave: (String) -> Unit,
    onDeleteRequest: () -> Unit
) {
    var name by remember(category) { mutableStateOf(category?.name ?: "") }
    
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.padding(16.dp).testTag(TestTags.EDIT_CATEGORY_ROOT)) {
        Text("Edit Category", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Category Name") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.EDIT_CATEGORY_NAME_FIELD)
                .focusRequester(focusRequester),
            enabled = !isDefaultCategory
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onSave(name) },
                modifier = Modifier.weight(1f).padding(end = 8.dp).testTag(TestTags.EDIT_CATEGORY_SAVE),
                enabled = !isDefaultCategory
            ) {
                Text("Save")
            }
            Button(
                onClick = onDeleteRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.weight(1f).padding(start = 8.dp).testTag(TestTags.EDIT_CATEGORY_DELETE)
            ) {
                Text("Delete")
            }
        }
    }
}

