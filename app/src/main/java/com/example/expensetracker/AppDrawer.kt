package com.example.expensetracker

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    navController: NavController,
    drawerState: DrawerState,
    scope: CoroutineScope
) {
    ModalDrawerSheet {
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_home)) },
            selected = navController.currentDestination?.route == "home",
            onClick = { navController.navigate("home"); scope.launch { drawerState.close() } },
            modifier = Modifier.padding(8.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_accounts)) },
            selected = navController.currentDestination?.route == "accounts",
            onClick = { navController.navigate("accounts"); scope.launch { drawerState.close() } },
            modifier = Modifier.padding(8.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Category, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_categories)) },
            selected = navController.currentDestination?.route == "categories",
            onClick = { navController.navigate("categories"); scope.launch { drawerState.close() } },
            modifier = Modifier.padding(8.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Money, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_currencies)) },
            selected = navController.currentDestination?.route == "currencies",
            onClick = { navController.navigate("currencies"); scope.launch { drawerState.close() } },
            modifier = Modifier.padding(8.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
            selected = navController.currentDestination?.route == "settings",
            onClick = { navController.navigate("settings"); scope.launch { drawerState.close() } },
            modifier = Modifier.padding(8.dp)
        )
    }
}