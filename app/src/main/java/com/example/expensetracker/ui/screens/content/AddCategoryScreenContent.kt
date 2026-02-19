package com.example.expensetracker.ui.screens.content

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.expensetracker.data.Category
import com.example.expensetracker.ui.TestTags

/**
 * State holder for AddCategoryScreen content.
 */
data class AddCategoryState(
    val categoryName: String = ""
)

/**
 * Callbacks for AddCategoryScreen content.
 */
data class AddCategoryCallbacks(
    val onNameChange: (String) -> Unit = {},
    val onSave: (Category) -> Unit = {}
)

/**
 * Pure UI content composable for AddCategoryScreen.
 * Accepts state + callbacks, no ViewModel or NavController dependencies.
 */
@Composable
fun AddCategoryScreenContent(
    state: AddCategoryState,
    callbacks: AddCategoryCallbacks,
    modifier: Modifier = Modifier
) {
    var localName by remember(state.categoryName) { mutableStateOf(state.categoryName) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = modifier.padding(16.dp).testTag(TestTags.ADD_CATEGORY_ROOT)) {
        Text("Add Category", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = localName,
            onValueChange = {
                localName = it
                callbacks.onNameChange(it)
            },
            label = { Text("Category Name") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.ADD_CATEGORY_NAME_FIELD)
                .focusRequester(focusRequester)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val newCategory = Category(name = localName)
                callbacks.onSave(newCategory)
            },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.ADD_CATEGORY_SAVE)
        ) {
            Text("Save")
        }
    }
}
