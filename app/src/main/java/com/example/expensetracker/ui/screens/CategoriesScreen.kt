package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.viewmodel.ExpenseViewModel

@Composable
fun CategoriesScreen(viewModel: ExpenseViewModel, navController: NavController) {
    val categories by viewModel.allCategories.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Categories", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { navController.navigate("addCategory") }) {
                Text("Create New")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (categories.isEmpty()) {
            Text("No categories yet. Tap 'Create New' to add one.")
        } else {
            LazyColumn {
                items(categories) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it.name)
                        Button(onClick = { navController.navigate("editCategory/${it.id}") }) {
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }
}
