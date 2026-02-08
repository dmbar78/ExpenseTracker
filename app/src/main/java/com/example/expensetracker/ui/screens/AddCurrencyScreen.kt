package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Currency
import com.example.expensetracker.viewmodel.ExpenseViewModel
import com.example.expensetracker.ui.TestTags
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCurrencyScreen(
    currencyId: Int,
    viewModel: ExpenseViewModel,
    navController: NavController
) {
    val isEditMode = currencyId > 0
    val scope = rememberCoroutineScope()
    
    // ISO 4217 Data Source
    val allCurrencies = remember { 
        java.util.Currency.getAvailableCurrencies()
            .toList()
            .sortedBy { it.currencyCode } 
    }

    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    
    // Search states for dropdowns
    var codeExpanded by remember { mutableStateOf(false) }
    var nameExpanded by remember { mutableStateOf(false) }
    
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverrideDialog by remember { mutableStateOf(false) }
    var overrideRateInput by remember { mutableStateOf("") }
    var latestRateDisplay by remember { mutableStateOf<String?>(null) }
    
    val currency by viewModel.selectedCurrency.collectAsState()
    val defaultCurrencyCode by viewModel.defaultCurrencyCode.collectAsState()

    // Load currency for editing
    LaunchedEffect(currencyId) {
        if (isEditMode) {
            viewModel.loadCurrency(currencyId)
        }
    }
    
    // Prefill fields when currency is loaded
    LaunchedEffect(currency) {
        if (isEditMode && currency != null) {
            code = currency!!.code
            name = currency!!.name
            // Load latest rate display
            latestRateDisplay = viewModel.getLatestRateDisplay(currency!!.code)
        }
    }

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
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Currency") },
            text = { Text("Are you sure you want to delete this currency?") },
            confirmButton = {
                Button(
                    onClick = {
                        currency?.let { viewModel.deleteCurrency(it) }
                        showDeleteDialog = false
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Override rate dialog
    if (showOverrideDialog) {
        OverrideRateDialog(
            currencyCode = code,
            defaultCurrencyCode = defaultCurrencyCode,
            rateInput = overrideRateInput,
            onRateInputChange = { overrideRateInput = it },
            onConfirm = {
                val rate = parseRateInput(overrideRateInput)
                if (rate != null && rate > BigDecimal.ZERO) {
                    scope.launch {
                        viewModel.setManualRateOverride(code, rate)
                        latestRateDisplay = viewModel.getLatestRateDisplay(code)
                    }
                    showOverrideDialog = false
                    overrideRateInput = ""
                } else {
                    errorMessage = "Please enter a valid rate greater than 0"
                    showErrorDialog = true
                }
            },
            onDismiss = {
                showOverrideDialog = false
                overrideRateInput = ""
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = if (isEditMode) "Edit Currency" else "Add Currency",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isEditMode) {
            OutlinedTextField(
                value = code,
                onValueChange = {},
                label = { Text("Currency Code") },
                modifier = Modifier.fillMaxWidth().testTag(TestTags.ADD_CURRENCY_CODE_FIELD),
                enabled = false
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = {},
                label = { Text("Currency Name") },
                modifier = Modifier.fillMaxWidth().testTag(TestTags.ADD_CURRENCY_NAME_FIELD),
                enabled = false
            )
        } else {
            // Filter logic
            val filteredByCode = remember(code, allCurrencies) {
                allCurrencies.filter {
                    it.currencyCode.contains(code, ignoreCase = true)
                }
            }
            
            ExposedDropdownMenuBox(
                expanded = codeExpanded,
                onExpandedChange = { codeExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.uppercase()
                        codeExpanded = true
                    },
                    label = { Text("Currency Code (Search)") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).testTag(TestTags.ADD_CURRENCY_CODE_FIELD),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = codeExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                
                if (filteredByCode.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = codeExpanded,
                        onDismissRequest = { codeExpanded = false }
                    ) {
                        filteredByCode.take(15).forEach { currency ->
                            DropdownMenuItem(
                                text = { Text("${currency.currencyCode} - ${currency.displayName}") },
                                onClick = {
                                    code = currency.currencyCode
                                    name = currency.displayName
                                    codeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val filteredByName = remember(name, allCurrencies) {
                allCurrencies.filter {
                    it.displayName.contains(name, ignoreCase = true)
                }
            }
            
            ExposedDropdownMenuBox(
                expanded = nameExpanded,
                onExpandedChange = { nameExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameExpanded = true
                    },
                    label = { Text("Currency Name (Search)") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).testTag(TestTags.ADD_CURRENCY_NAME_FIELD),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nameExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                
                if (filteredByName.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = nameExpanded,
                        onDismissRequest = { nameExpanded = false }
                    ) {
                        filteredByName.take(15).forEach { currency ->
                            DropdownMenuItem(
                                text = { Text(currency.displayName) },
                                onClick = {
                                    code = currency.currencyCode
                                    name = currency.displayName
                                    nameExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        // Latest rate display (edit mode only, when not same as default)
        if (isEditMode && code != defaultCurrencyCode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Exchange Rate",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = latestRateDisplay ?: "No rate available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (latestRateDisplay != null) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showOverrideDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Override Rate")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Button(
            onClick = {
                // Validation: Check if Code and Name match a valid ISO 4217 currency
                // We perform this check strictly for new currencies to ensure compliance.
                // For editing, we might be more lenient if we allowed custom names, but based on request, we enforce compliance.
                // Actually, for Edit mode, Code is fixed. If we only validate on creation, it solves "entered in code and name fields".
                
                if (!isEditMode) {
                   val isValid = allCurrencies.any { 
                       it.currencyCode.equals(code, ignoreCase = true) && 
                       it.displayName.equals(name, ignoreCase = true) 
                   }
                   
                   if (!isValid) {
                       errorMessage = "Invalid currency. Please select a valid currency (Code and Name) from the list."
                       showErrorDialog = true
                       return@Button
                   }
                }

                if (isEditMode) {
                    val updatedCurrency = currency?.copy(name = name)
                    updatedCurrency?.let { 
                        viewModel.updateCurrency(it)
                        navController.popBackStack()
                    }
                } else {
                    val newCurrency = Currency(code = code.uppercase(), name = name)
                    viewModel.insertCurrency(newCurrency)
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.ADD_CURRENCY_SAVE)
        ) {
            Text("Save")
        }
        
        // Delete button (edit mode only)
        if (isEditMode) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("Delete Currency")
            }
        }
    }
}

@Composable
private fun OverrideRateDialog(
    currencyCode: String,
    defaultCurrencyCode: String,
    rateInput: String,
    onRateInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Override Exchange Rate") },
        text = {
            Column {
                Text(
                    text = "Enter the exchange rate from $currencyCode to $defaultCurrencyCode:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = rateInput,
                    onValueChange = onRateInputChange,
                    label = { Text("1 $currencyCode = ? $defaultCurrencyCode") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Parse rate input handling both dot and comma decimal separators.
 */
private fun parseRateInput(input: String): BigDecimal? {
    if (input.isBlank()) return null
    return try {
        val normalized = input.trim().replace(",", ".")
        BigDecimal(normalized)
    } catch (e: NumberFormatException) {
        null
    }
}
