package com.example.expensetracker.ui.screens.content

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Currency
import androidx.compose.ui.res.stringResource
import com.example.expensetracker.R
import com.example.expensetracker.ui.TestTags
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * State holder for AddAccountScreen content.
 */
data class AddAccountState(
    val name: String = "",
    val balance: String = "",
    val currencyCode: String = ""
)

/**
 * Callbacks for AddAccountScreen content.
 */
data class AddAccountCallbacks(
    val onNameChange: (String) -> Unit = {},
    val onBalanceChange: (String) -> Unit = {},
    val onCurrencySelect: (Currency) -> Unit = {},
    val onSave: (Account) -> Unit = {}
)

/**
 * Pure UI content composable for AddAccountScreen.
 * Accepts state + callbacks, no ViewModel or NavController dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreenContent(
    state: AddAccountState,
    currencies: List<Currency>,
    callbacks: AddAccountCallbacks,
    modifier: Modifier = Modifier
) {
    var localName by remember(state.name) { mutableStateOf(state.name) }
    var localBalance by remember(state.balance) { mutableStateOf(state.balance) }
    var localCurrencyCode by remember(state.currencyCode) { mutableStateOf(state.currencyCode) }
    var isCurrencyDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp).testTag(TestTags.ADD_ACCOUNT_ROOT)) {
        Text(stringResource(R.string.title_add_account), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = localName,
            onValueChange = {
                localName = it
                callbacks.onNameChange(it)
            },
            label = { Text(stringResource(R.string.lbl_account_name)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.ADD_ACCOUNT_NAME_FIELD)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = localBalance,
            onValueChange = {
                localBalance = it
                callbacks.onBalanceChange(it)
            },
            label = { Text(stringResource(R.string.lbl_initial_balance)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.ADD_ACCOUNT_BALANCE_FIELD),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = isCurrencyDropdownExpanded,
            onExpandedChange = { isCurrencyDropdownExpanded = !isCurrencyDropdownExpanded },
            modifier = Modifier.testTag(TestTags.ADD_ACCOUNT_CURRENCY_DROPDOWN)
        ) {
            OutlinedTextField(
                value = currencies.find { it.code == localCurrencyCode }?.name ?: "",
                onValueChange = {},
                label = { Text(stringResource(R.string.lbl_currency)) },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCurrencyDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth().testTag(TestTags.ADD_ACCOUNT_CURRENCY_VALUE)
            )
            ExposedDropdownMenu(
                expanded = isCurrencyDropdownExpanded,
                onDismissRequest = { isCurrencyDropdownExpanded = false }
            ) {
                currencies.forEach { currencyItem ->
                    DropdownMenuItem(
                        text = { Text(currencyItem.name) },
                        onClick = {
                            localCurrencyCode = currencyItem.code
                            isCurrencyDropdownExpanded = false
                            callbacks.onCurrencySelect(currencyItem)
                        },
                        modifier = Modifier.testTag(TestTags.CURRENCY_OPTION_PREFIX + currencyItem.code)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val parsedBalance = parseAccountMoneyInputContent(localBalance) ?: BigDecimal.ZERO
                val newAccount = Account(
                    name = localName,
                    balance = parsedBalance.setScale(2, RoundingMode.HALF_UP),
                    currency = localCurrencyCode
                )
                callbacks.onSave(newAccount)
            },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.ADD_ACCOUNT_SAVE)
        ) {
            Text(stringResource(R.string.btn_save))
        }
    }
}

/**
 * Parses a money input string handling both dot and comma decimal separators.
 */
internal fun parseAccountMoneyInputContent(input: String): BigDecimal? {
    if (input.isBlank()) return null

    val cleaned = input.trim()
    val lastDotIndex = cleaned.lastIndexOf('.')
    val lastCommaIndex = cleaned.lastIndexOf(',')

    val normalizedString = when {
        lastDotIndex == -1 && lastCommaIndex == -1 -> cleaned
        lastCommaIndex == -1 -> {
            val afterDot = cleaned.length - lastDotIndex - 1
            if (cleaned.count { it == '.' } == 1 && afterDot <= 2) {
                cleaned
            } else {
                cleaned.replace(".", "")
            }
        }
        lastDotIndex == -1 -> {
            val afterComma = cleaned.length - lastCommaIndex - 1
            if (cleaned.count { it == ',' } == 1 && afterComma <= 2) {
                cleaned.replace(',', '.')
            } else {
                cleaned.replace(",", "")
            }
        }
        lastDotIndex > lastCommaIndex -> {
            cleaned.replace(",", "")
        }
        else -> {
            cleaned.replace(".", "").replace(',', '.')
        }
    }

    return try {
        BigDecimal(normalizedString).setScale(2, RoundingMode.HALF_UP)
    } catch (e: NumberFormatException) {
        null
    }
}
