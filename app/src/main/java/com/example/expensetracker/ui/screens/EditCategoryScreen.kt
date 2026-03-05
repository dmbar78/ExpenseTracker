package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
            title = { Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.title_error)) },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { showErrorDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.btn_ok))
                }
            }
        )
    }
    
    if (showCannotDeleteDefaultDialog) {
        AlertDialog(
            onDismissRequest = { showCannotDeleteDefaultDialog = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.title_cannot_delete)) },
            text = { Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.msg_cannot_delete_default_category)) },
            confirmButton = {
                Button(onClick = { showCannotDeleteDefaultDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.btn_ok))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.title_delete_category)) },
            text = { Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.msg_delete_category_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        category?.let { viewModel.deleteCategory(it) }
                        showDeleteDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.btn_yes))
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.btn_no))
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
    var name by remember(category) {
        mutableStateOf(
            TextFieldValue(
                text = category?.name ?: "",
                selection = TextRange((category?.name ?: "").length)
            )
        )
    }
    
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.padding(16.dp).testTag(TestTags.EDIT_CATEGORY_ROOT)) {
        Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.title_edit_category), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.lbl_category_name)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.EDIT_CATEGORY_NAME_FIELD)
                .focusRequester(focusRequester),
            enabled = !isDefaultCategory
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onSave(name.text) },
                modifier = Modifier.weight(1f).padding(end = 8.dp).testTag(TestTags.EDIT_CATEGORY_SAVE),
                enabled = !isDefaultCategory
            ) {
                Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.btn_save))
            }
            Button(
                onClick = onDeleteRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.weight(1f).padding(start = 8.dp).testTag(TestTags.EDIT_CATEGORY_DELETE)
            ) {
                Text(androidx.compose.ui.res.stringResource(com.example.expensetracker.R.string.btn_delete))
            }
        }
    }
}

