package com.example.expensetracker

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.expensetracker.ui.screens.*
import com.example.expensetracker.viewmodel.ExpenseViewModel
import java.math.BigDecimal

@Composable
fun NavGraph(viewModel: ExpenseViewModel, navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = "home", modifier = modifier) {
        composable("home") {
            HomeScreen(viewModel = viewModel, navController = navController)
        }
        composable("accounts") {
            AccountsScreen(viewModel = viewModel, navController = navController)
        }
        composable(
            route = "addAccount?accountName={accountName}",
            arguments = listOf(navArgument("accountName") { type = NavType.StringType; nullable = true })
        ) {
            val accountName = it.arguments?.getString("accountName")
            AddAccountScreen(viewModel = viewModel, navController = navController, accountName = accountName)
        }
        composable(
            route = "editAccount/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.IntType })
        ) {
            val accountId = it.arguments?.getInt("accountId") ?: 0
            EditAccountScreen(accountId = accountId, viewModel = viewModel, navController = navController)
        }
        composable("categories") {
            CategoriesScreen(viewModel = viewModel, navController = navController)
        }
        composable(
            route = "addCategory?categoryName={categoryName}",
            arguments = listOf(navArgument("categoryName") { type = NavType.StringType; nullable = true })
        ) {
            val categoryName = it.arguments?.getString("categoryName")
            AddCategoryScreen(viewModel = viewModel, navController = navController, categoryName = categoryName)
        }
        composable(
            route = "editCategory/{categoryId}",
            arguments = listOf(navArgument("categoryId") { type = NavType.IntType })
        ) {
            val categoryId = it.arguments?.getInt("categoryId") ?: 0
            EditCategoryScreen(categoryId = categoryId, viewModel = viewModel, navController = navController)
        }
        composable("currencies") {
            CurrenciesScreen(viewModel = viewModel, navController = navController)
        }
        composable("addCurrency") {
            AddCurrencyScreen(currencyId = 0, viewModel = viewModel, navController = navController)
        }
        composable(
            route = "editCurrency/{currencyId}",
            arguments = listOf(navArgument("currencyId") { type = NavType.IntType })
        ) {
            val currencyId = it.arguments?.getInt("currencyId") ?: 0
            AddCurrencyScreen(currencyId = currencyId, viewModel = viewModel, navController = navController)
        }
        composable("settings") {
            SettingsScreen(viewModel = viewModel, navController = navController)
        }
        composable(
            route = "editExpense/{expenseId}?accountName={accountName}&amount={amount}&categoryName={categoryName}&type={type}&expenseDateMillis={expenseDateMillis}&accountError={accountError}&categoryError={categoryError}&defaultAccountUsed={defaultAccountUsed}",
            arguments = listOf(
                navArgument("expenseId") { type = NavType.IntType },
                navArgument("accountName") { type = NavType.StringType; nullable = true },
                navArgument("amount") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("categoryName") { type = NavType.StringType; nullable = true },
                navArgument("type") { type = NavType.StringType; nullable = true },
                navArgument("expenseDateMillis") { type = NavType.LongType; defaultValue = 0L },
                navArgument("accountError") { type = NavType.BoolType; defaultValue = false },
                navArgument("categoryError") { type = NavType.BoolType; defaultValue = false },
                navArgument("defaultAccountUsed") { type = NavType.BoolType; defaultValue = false }
            )
        ) {
            val expenseId = it.arguments?.getInt("expenseId") ?: 0
            val accountName = it.arguments?.getString("accountName")
            val amountStr = it.arguments?.getString("amount")
            val amount = amountStr?.let { str -> runCatching { BigDecimal(str) }.getOrNull() }
            val categoryName = it.arguments?.getString("categoryName")
            val type = it.arguments?.getString("type")
            val expenseDateMillis = it.arguments?.getLong("expenseDateMillis") ?: 0L
            val accountError = it.arguments?.getBoolean("accountError") ?: false
            val categoryError = it.arguments?.getBoolean("categoryError") ?: false
            val defaultAccountUsed = it.arguments?.getBoolean("defaultAccountUsed") ?: false

            EditExpenseScreen(
                expenseId = expenseId,
                viewModel = viewModel,
                navController = navController,
                initialAccountName = accountName,
                initialAmount = amount,
                initialCategoryName = categoryName,
                initialType = type,
                initialExpenseDateMillis = expenseDateMillis,
                initialAccountError = accountError,
                initialCategoryError = categoryError,
                defaultAccountUsed = defaultAccountUsed
            )
        }
        composable(
            route = "editTransfer/{transferId}?sourceAccountName={sourceAccountName}&destAccountName={destAccountName}&amount={amount}&destAmount={destAmount}&transferDateMillis={transferDateMillis}&sourceAccountError={sourceAccountError}&destAccountError={destAccountError}&defaultAccountUsed={defaultAccountUsed}",
            arguments = listOf(
                navArgument("transferId") { type = NavType.IntType },
                navArgument("sourceAccountName") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("destAccountName") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("amount") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("destAmount") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("transferDateMillis") { type = NavType.LongType; defaultValue = 0L },
                navArgument("sourceAccountError") { type = NavType.BoolType; defaultValue = false },
                navArgument("destAccountError") { type = NavType.BoolType; defaultValue = false },
                navArgument("defaultAccountUsed") { type = NavType.BoolType; defaultValue = false }
            )
        ) {
            val transferId = it.arguments?.getInt("transferId") ?: 0
            val sourceAccountName = it.arguments?.getString("sourceAccountName")
            val destAccountName = it.arguments?.getString("destAccountName")
            val amountStr = it.arguments?.getString("amount")
            val amount = amountStr?.let { str -> runCatching { BigDecimal(str) }.getOrNull() }
            val destAmountStr = it.arguments?.getString("destAmount")
            val destAmount = destAmountStr?.let { str -> runCatching { BigDecimal(str) }.getOrNull() }
            val transferDateMillis = it.arguments?.getLong("transferDateMillis") ?: 0L
            val sourceAccountError = it.arguments?.getBoolean("sourceAccountError") ?: false
            val destAccountError = it.arguments?.getBoolean("destAccountError") ?: false
            val defaultAccountUsed = it.arguments?.getBoolean("defaultAccountUsed") ?: false
            
            EditTransferScreen(
                transferId = transferId,
                viewModel = viewModel,
                navController = navController,
                initialSourceAccountName = sourceAccountName,
                initialDestAccountName = destAccountName,
                initialAmount = amount,
                initialDestAmount = destAmount,
                initialTransferDateMillis = transferDateMillis,
                initialSourceAccountError = sourceAccountError,
                initialDestAccountError = destAccountError,
                defaultAccountUsed = defaultAccountUsed
            )
        }
    }
}
