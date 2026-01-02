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
        composable("addAccount") {
            AddAccountScreen(viewModel = viewModel, navController = navController)
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
            AddCurrencyScreen(viewModel = viewModel, navController = navController)
        }
        composable(
            route = "editExpense/{expenseId}?accountName={accountName}&amount={amount}&categoryName={categoryName}&type={type}&accountError={accountError}&categoryError={categoryError}",
            arguments = listOf(
                navArgument("expenseId") { type = NavType.IntType },
                navArgument("accountName") { type = NavType.StringType; nullable = true },
                navArgument("amount") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("categoryName") { type = NavType.StringType; nullable = true },
                navArgument("type") { type = NavType.StringType; nullable = true },
                navArgument("accountError") { type = NavType.BoolType; defaultValue = false },
                navArgument("categoryError") { type = NavType.BoolType; defaultValue = false }
            )
        ) {
            val expenseId = it.arguments?.getInt("expenseId") ?: 0
            val accountName = it.arguments?.getString("accountName")
            val amountStr = it.arguments?.getString("amount")
            val amount = amountStr?.let { str -> runCatching { BigDecimal(str) }.getOrNull() }
            val categoryName = it.arguments?.getString("categoryName")
            val type = it.arguments?.getString("type")
            val accountError = it.arguments?.getBoolean("accountError") ?: false
            val categoryError = it.arguments?.getBoolean("categoryError") ?: false

            EditExpenseScreen(
                expenseId = expenseId,
                viewModel = viewModel,
                navController = navController,
                initialAccountName = accountName,
                initialAmount = amount,
                initialCategoryName = categoryName,
                initialType = type,
                initialAccountError = accountError,
                initialCategoryError = categoryError
            )
        }
        composable(
            route = "editTransfer/{transferId}",
            arguments = listOf(navArgument("transferId") { type = NavType.IntType })
        ) {
            val transferId = it.arguments?.getInt("transferId") ?: 0
            EditTransferScreen(transferId = transferId, viewModel = viewModel, navController = navController)
        }
        composable(
            route = "transferAccountNotFound/{sourceAccountName}/{destAccountName}",
            arguments = listOf(
                navArgument("sourceAccountName") { type = NavType.StringType },
                navArgument("destAccountName") { type = NavType.StringType }
            )
        ) {
            val sourceAccountName = it.arguments?.getString("sourceAccountName") ?: ""
            val destAccountName = it.arguments?.getString("destAccountName") ?: ""
            TransferAccountNotFoundScreen(viewModel = viewModel, navController = navController, sourceAccountName = sourceAccountName, destAccountName = destAccountName)
        }
    }
}
