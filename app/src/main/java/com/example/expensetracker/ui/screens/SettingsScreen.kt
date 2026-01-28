package com.example.expensetracker.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Currency
import com.example.expensetracker.data.SecurityManager
import com.example.expensetracker.ui.TestTags
import com.example.expensetracker.viewmodel.BackupOperationState
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ExpenseViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val currencies by viewModel.allCurrencies.collectAsState()
    val defaultCurrencyCode by viewModel.defaultCurrencyCode.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val scope = rememberCoroutineScope()
    
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var selectedCurrency by remember(defaultCurrencyCode) { mutableStateOf(defaultCurrencyCode) }
    
    // State for the EUR pivot rate prompt
    var showPivotRateDialog by remember { mutableStateOf(false) }
    var pivotRateInput by remember { mutableStateOf("") }
    var pendingCurrencyCode by remember { mutableStateOf("") }
    var isCheckingPivot by remember { mutableStateOf(false) }

    // State for Restore Confirmation
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Launchers for Backup/Restore
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingRestoreUri = it
            showRestoreConfirmation = true
        }
    }

    // Handle Backup State
    LaunchedEffect(backupState) {
        when (val state = backupState) {
            is BackupOperationState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetBackupState()
            }
            is BackupOperationState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetBackupState()
            }
            else -> {}
        }
    }

    if (backupState is BackupOperationState.Loading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Processing") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Please wait...")
                }
            },
            confirmButton = {}
        )
    }

    // Restore Confirmation Dialog
    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            title = { Text("Confirm Restore") },
            text = { Text("Restoring from backup will replace ALL existing data. This action cannot be undone.\n\nAre you sure you want to proceed?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri?.let { viewModel.importBackup(it) }
                        showRestoreConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // State for PIN Lock Screen
    var showPinLock by remember { mutableStateOf(false) }
    var pinLockMode by remember { mutableStateOf(PinScreenMode.Create) }
    
    // Check PIN Status
    var isPinSet by remember { mutableStateOf(SecurityManager.isPinSet(context)) }
    var isBiometricEnabled by remember { mutableStateOf(SecurityManager.isBiometricEnabled(context)) }

    if (showPinLock) {
        // Overlay PIN Screen
        Surface(modifier = Modifier.fillMaxSize().zIndex(1f)) {
            PinLockScreen(
                mode = pinLockMode,
                onSuccess = {
                    showPinLock = false
                    isPinSet = SecurityManager.isPinSet(context) // Refresh
                    isBiometricEnabled = SecurityManager.isBiometricEnabled(context)
                    // If we were changing, we might need a 2-step flow (Old -> New).
                    // For simplicity, if mode was Change, we just verified old. Now launch Create for new.
                    if (pinLockMode == PinScreenMode.Change) {
                        pinLockMode = PinScreenMode.Create
                        showPinLock = true 
                    } else if (pinLockMode == PinScreenMode.Remove) {
                        SecurityManager.removePin(context)
                        isPinSet = false
                        isBiometricEnabled = false
                        Toast.makeText(context, "PIN removed", Toast.LENGTH_SHORT).show()
                    }
                },
                onCancel = { showPinLock = false }
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Security Section
        Text(
            text = "Security",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        if (isPinSet) {
            SettingsItem(
                title = "Change PIN",
                value = "******",
                onClick = {
                    pinLockMode = PinScreenMode.Change
                    showPinLock = true
                },
                modifier = Modifier.testTag(TestTags.SETTINGS_CHANGE_PIN)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                title = "Remove PIN",
                value = "Disable Security",
                onClick = {
                    pinLockMode = PinScreenMode.Remove
                    showPinLock = true
                },
                modifier = Modifier.testTag(TestTags.SETTINGS_REMOVE_PIN)
            )
            // Actually, secure removal is better.
            // Let's implement simple "Switch" logic for Biometrics
            Row(
                 modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Biometric Unlock", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isBiometricEnabled,
                    onCheckedChange = { params ->
                         if (params) {
                             // Enabling: Need to confirm PIN first or just enable if keys are ready?
                             // Plan said: "Require entering the PIN once before enabling biometrics"
                             // We are masterKey loaded? If we just entered Settings, maybe locked?
                             // If SecurityManager.isLocked(), we must unlock DEK first.
                             if (SecurityManager.isLocked()) {
                                 Toast.makeText(context, "Please unlock with PIN first", Toast.LENGTH_SHORT).show()
                                 // Trigger PIN unlock?
                             } else {
                             // Check if device supports biometric auth
                             val biometricManager = androidx.biometric.BiometricManager.from(context)
                             val result = biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                             
                             if (result == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                                 val activity = context as? androidx.fragment.app.FragmentActivity
                                 if (activity == null) {
                                     Toast.makeText(context, "Error: Context is not a FragmentActivity", Toast.LENGTH_SHORT).show()
                                     return@Switch
                                 }

                                 try {
                                     // 1. Get Cipher
                                     val cipher = SecurityManager.getBiometricEnrollmentCipher()
                                     val cryptoObject = androidx.biometric.BiometricPrompt.CryptoObject(cipher)
                                     
                                     // 2. Show Prompt
                                     val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                                     val callback = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                         override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                             super.onAuthenticationSucceeded(result)
                                             try {
                                                 val authenticatedCryptoObject = result.cryptoObject
                                                 if (authenticatedCryptoObject != null) {
                                                     SecurityManager.finalizeBiometricEnable(context, authenticatedCryptoObject)
                                                     isBiometricEnabled = true
                                                     Toast.makeText(context, "Biometric enabled", Toast.LENGTH_SHORT).show()
                                                 }
                                             } catch (e: Exception) {
                                                 e.printStackTrace()
                                                 Toast.makeText(context, "Finalization failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                 isBiometricEnabled = false // Revert visual switch
                                             }
                                         }
                                         
                                         override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                             super.onAuthenticationError(errorCode, errString)
                                             Toast.makeText(context, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                                             isBiometricEnabled = false // Revert visual switch
                                         }
                                         
                                         override fun onAuthenticationFailed() {
                                             super.onAuthenticationFailed()
                                             // Soft failure, system usually handles retries. 
                                             // But we might want to ensure switch stays off until success.
                                         }
                                     }
                                     
                                     val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                         .setTitle("Enable Biometric Unlock")
                                         .setSubtitle("Confirm your identity to enable biometric unlock")
                                         .setNegativeButtonText("Cancel")
                                         .build()
                                         
                                     val biometricPrompt = androidx.biometric.BiometricPrompt(activity, executor, callback)
                                     biometricPrompt.authenticate(promptInfo, cryptoObject)
                                     
                                     // Optimistically set true? No, wait for callback. 
                                     // But the Switch UI might need to bounce back if we don't set it true here?
                                     // Better to keep it false and only set true on success. 
                                     // BUT `onCheckedChange` is called. The `isBiometricEnabled` state is driving the switch.
                                     // Use a local loading state if needed, or accepting that it stays off until auth succeeds.
                                 } catch (e: Exception) {
                                     e.printStackTrace()
                                     Toast.makeText(context, "Setup failed: ${e.message}", Toast.LENGTH_LONG).show()
                                 }
                             } else {
                                 val msg = when(result) {
                                     androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware"
                                     androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware unavailable"
                                     androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometrics enrolled. Check phone settings."
                                     else -> "Biometric auth not available (Code: $result)"
                                 }
                                 Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                 // Reset switch visual state since we failed
                                 // (Note: isBiometricEnabled is already false, but to be sure we don't flip it)
                             }
                             }
                         } else {
                             SecurityManager.disableBiometric(context)
                             isBiometricEnabled = false
                         }
                    },
                    modifier = Modifier.testTag(TestTags.SETTINGS_BIOMETRIC_SWITCH)
                )
            }

        } else {
            SettingsItem(
                title = "Add PIN",
                value = "Enable Security",
                onClick = {
                    pinLockMode = PinScreenMode.Create
                    showPinLock = true
                },
                modifier = Modifier.testTag(TestTags.SETTINGS_ADD_PIN)
            )
        }
        
        // Default Currency setting
        Text(
            text = "General",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        SettingsItem(
            title = "Default currency",
            value = defaultCurrencyCode,
            onClick = { 
                selectedCurrency = defaultCurrencyCode
                showCurrencyPicker = true 
            }
        )

        // Data Management Section
        Text(
            text = "Data Management",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        SettingsItem(
            title = "Export Backup",
            value = "JSON",
            onClick = {
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                exportLauncher.launch("expense-tracker-backup-$timestamp.json")
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            title = "Restore from Backup",
            value = "Select File",
            onClick = {
                importLauncher.launch(arrayOf("application/json"))
            }
        )
    }
    
    // Currency picker dialog
    if (showCurrencyPicker) {
        DefaultCurrencyPickerDialog(
            currencies = currencies,
            selectedCurrencyCode = selectedCurrency,
            isCheckingPivot = isCheckingPivot,
            onCurrencySelected = { newCode ->
                // When selection changes, check if EUR pivot exists
                if (newCode != selectedCurrency && newCode != "EUR") {
                    isCheckingPivot = true
                    scope.launch {
                        val pivotExists = viewModel.ensureEurPivotExists(newCode)
                        isCheckingPivot = false
                        if (pivotExists) {
                            selectedCurrency = newCode
                        } else {
                            // No pivot available - prompt for manual entry
                            pendingCurrencyCode = newCode
                            pivotRateInput = ""
                            showPivotRateDialog = true
                        }
                    }
                } else {
                    selectedCurrency = newCode
                }
            },
            onConfirm = {
                viewModel.setDefaultCurrency(selectedCurrency)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false }
        )
    }
    
    // EUR pivot rate entry dialog
    if (showPivotRateDialog) {
        EurPivotRateDialog(
            currencyCode = pendingCurrencyCode,
            rateInput = pivotRateInput,
            onRateInputChange = { pivotRateInput = it },
            onConfirm = {
                val rate = parseRateInput(pivotRateInput)
                if (rate != null && rate > BigDecimal.ZERO) {
                    scope.launch {
                        viewModel.setManualEurPivot(pendingCurrencyCode, rate)
                        selectedCurrency = pendingCurrencyCode
                    }
                    showPivotRateDialog = false
                    pivotRateInput = ""
                }
            },
            onDismiss = {
                // Cancel - keep previous selection
                showPivotRateDialog = false
                pivotRateInput = ""
            }
        )
    }
}

@Composable
private fun SettingsItem(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "($value)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultCurrencyPickerDialog(
    currencies: List<Currency>,
    selectedCurrencyCode: String,
    isCheckingPivot: Boolean,
    onCurrencySelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Default Currency") },
        text = {
            Column {
                Text(
                    text = "Choose the currency to use for totals and conversions.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Currency dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!isCheckingPivot) expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCurrencyCode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Currency") },
                        trailingIcon = {
                            if (isCheckingPivot) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !isCheckingPivot
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded && !isCheckingPivot,
                        onDismissRequest = { expanded = false }
                    ) {
                        currencies.forEach { currency ->
                            DropdownMenuItem(
                                text = { Text("${currency.code} - ${currency.name}") },
                                onClick = {
                                    expanded = false
                                    onCurrencySelected(currency.code)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isCheckingPivot
            ) {
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

@Composable
private fun EurPivotRateDialog(
    currencyCode: String,
    rateInput: String,
    onRateInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isValidRate = remember(rateInput) {
        val rate = parseRateInput(rateInput)
        rate != null && rate > BigDecimal.ZERO
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Exchange Rate") },
        text = {
            Column {
                Text(
                    text = "No exchange rate available for $currencyCode. Please enter the EUR to $currencyCode rate:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = rateInput,
                    onValueChange = onRateInputChange,
                    label = { Text("1 EUR = ? $currencyCode") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = rateInput.isNotBlank() && !isValidRate,
                    supportingText = if (rateInput.isNotBlank() && !isValidRate) {
                        { Text("Please enter a valid rate greater than 0") }
                    } else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isValidRate
            ) {
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
