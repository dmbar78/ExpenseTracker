package com.example.expensetracker.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Currency
import com.example.expensetracker.data.SecurityManager
import com.example.expensetracker.ui.TestTags
import com.example.expensetracker.viewmodel.BackupOperationState
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import androidx.compose.ui.res.stringResource
import com.example.expensetracker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ExpenseViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val currencies by viewModel.allCurrencies.collectAsState()
    val defaultCurrencyCode by viewModel.defaultCurrencyCode.collectAsState()
    val defaultExpenseAccountId by viewModel.defaultExpenseAccountId.collectAsState()
    val defaultTransferAccountId by viewModel.defaultTransferAccountId.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val scope = rememberCoroutineScope()
    
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var selectedCurrency by remember(defaultCurrencyCode) { mutableStateOf(defaultCurrencyCode) }
    
    // State for the EUR pivot rate prompt
    var showPivotRateDialog by remember { mutableStateOf(false) }
    var pivotRateInput by remember { mutableStateOf("") }
    var pendingCurrencyCode by remember { mutableStateOf("") }
    var isCheckingPivot by remember { mutableStateOf(false) }
    
    var showExpenseAccountPicker by remember { mutableStateOf(false) }
    var showTransferAccountPicker by remember { mutableStateOf(false) }

    // State for Restore Confirmation
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    // State for Export Encryption
    var showEncryptDialog by remember { mutableStateOf(false) }
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var exportPasswordInput by remember { mutableStateOf("") }
    
    // State for Import Password
    var restorePasswordInput by remember { mutableStateOf("") }

    // Launchers for Backup/Restore
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportBackup(it, pendingExportPassword) }
        pendingExportPassword = null
        exportPasswordInput = ""
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
            title = { Text(stringResource(R.string.title_processing)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.msg_please_wait))
                }
            },
            confirmButton = {}
        )
    }
    
    if (backupState is BackupOperationState.RequestPassword) {
        AlertDialog(
            onDismissRequest = { 
                viewModel.cancelRestore()
                restorePasswordInput = ""
            },
            title = { Text(stringResource(R.string.title_encrypted_backup)) },
            text = { 
                Column {
                    Text(stringResource(R.string.msg_encrypted_backup_password))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restorePasswordInput,
                        onValueChange = { restorePasswordInput = it },
                        label = { Text(stringResource(R.string.lbl_password)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.provideRestorePassword(restorePasswordInput)
                        restorePasswordInput = ""
                    },
                    enabled = restorePasswordInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.btn_restore))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        viewModel.cancelRestore()
                        restorePasswordInput = ""
                    }
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Restore Confirmation Dialog
    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            title = { Text(stringResource(R.string.title_confirm_restore)) },
            text = { Text(stringResource(R.string.msg_confirm_restore)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri?.let { viewModel.importBackup(it) }
                        showRestoreConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmation = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
    
    // Encrypt Backup Choice Dialog
    if (showEncryptDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptDialog = false },
            title = { Text(stringResource(R.string.title_encrypt_backup)) },
            text = { Text(stringResource(R.string.msg_encrypt_backup_ask)) },
            confirmButton = {
                TextButton(onClick = {
                    showEncryptDialog = false
                    showSetPasswordDialog = true
                }) {
                    Text(stringResource(R.string.btn_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showEncryptDialog = false
                    pendingExportPassword = null
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                    exportLauncher.launch("expense-tracker-backup-$timestamp.json")
                }) {
                    Text(stringResource(R.string.btn_no))
                }
            }
        )
    }
    
    // Set Password Dialog
    if (showSetPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showSetPasswordDialog = false },
            title = { Text(stringResource(R.string.title_set_backup_password)) },
            text = {
                Column {
                    Text(stringResource(R.string.msg_set_backup_password))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportPasswordInput,
                        onValueChange = { exportPasswordInput = it },
                        label = { Text(stringResource(R.string.lbl_password)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExportPassword = exportPasswordInput
                        showSetPasswordDialog = false
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        exportLauncher.launch("expense-tracker-backup-$timestamp.json")
                    },
                    enabled = exportPasswordInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.btn_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPasswordDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
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
                        isBiometricEnabled = false
                        Toast.makeText(context, context.getString(R.string.msg_pin_removed), Toast.LENGTH_SHORT).show()
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
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.title_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Security Section
        Text(
            text = stringResource(R.string.header_security),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        if (isPinSet) {
            SettingsItem(
                title = stringResource(R.string.title_change_pin),
                value = "******",
                onClick = {
                    pinLockMode = PinScreenMode.Change
                    showPinLock = true
                },
                modifier = Modifier.testTag(TestTags.SETTINGS_CHANGE_PIN)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                title = stringResource(R.string.title_remove_pin),
                value = stringResource(R.string.val_disable_security),
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
                Text(stringResource(R.string.title_enable_biometric), style = MaterialTheme.typography.bodyLarge)
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
                title = stringResource(R.string.title_add_pin),
                value = stringResource(R.string.val_enable_security),
                onClick = {
                    pinLockMode = PinScreenMode.Create
                    showPinLock = true
                },
                modifier = Modifier.testTag(TestTags.SETTINGS_ADD_PIN)
            )
        }
        
    // Default Currency setting
        Text(
            text = stringResource(R.string.header_general),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        SettingsItem(
            title = stringResource(R.string.title_default_expense_account),
            value = allAccounts.find { it.id == defaultExpenseAccountId }?.name ?: stringResource(R.string.val_none),
            onClick = { showExpenseAccountPicker = true }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SettingsItem(
            title = stringResource(R.string.title_default_transfer_account),
            value = allAccounts.find { it.id == defaultTransferAccountId }?.name ?: stringResource(R.string.val_none),
            onClick = { showTransferAccountPicker = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            title = stringResource(R.string.title_default_currency),
            value = defaultCurrencyCode,
            onClick = { 
                selectedCurrency = defaultCurrencyCode
                showCurrencyPicker = true 
            }
        )

        // Data Management Section
        Text(
            text = stringResource(R.string.header_data_management),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        SettingsItem(
            title = stringResource(R.string.title_export_backup),
            value = "JSON",
            onClick = {
                showEncryptDialog = true
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            title = stringResource(R.string.title_restore_backup),
            value = stringResource(R.string.val_select_file),
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
    
    if (showExpenseAccountPicker) {
        DefaultAccountPickerDialog(
            accounts = allAccounts,
            selectedAccountId = defaultExpenseAccountId,
            title = "Default Expense Account",
            onAccountSelected = { id ->
                viewModel.setDefaultExpenseAccount(id)
                showExpenseAccountPicker = false
            },
            onDismiss = { showExpenseAccountPicker = false }
        )
    }

    if (showTransferAccountPicker) {
        DefaultAccountPickerDialog(
            accounts = allAccounts,
            selectedAccountId = defaultTransferAccountId,
            title = "Default Transfer Account",
            onAccountSelected = { id ->
                viewModel.setDefaultTransferAccount(id)
                showTransferAccountPicker = false
            },
            onDismiss = { showTransferAccountPicker = false }
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
        title = { Text(stringResource(R.string.title_select_default_currency)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.msg_choose_currency),
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
                        label = { Text(stringResource(R.string.lbl_currency)) },
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
                Text(stringResource(R.string.btn_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
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
        title = { Text(stringResource(R.string.title_enter_exchange_rate)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.msg_enter_exchange_rate, currencyCode, currencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = rateInput,
                    onValueChange = onRateInputChange,
                    label = { Text(stringResource(R.string.lbl_exchange_rate_hint, currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = rateInput.isNotBlank() && !isValidRate,
                    supportingText = if (rateInput.isNotBlank() && !isValidRate) {
                        { Text(stringResource(R.string.err_invalid_rate)) }
                    } else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isValidRate
            ) {
                Text(stringResource(R.string.btn_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultAccountPickerDialog(
    accounts: List<Account>,
    selectedAccountId: Int?,
    title: String,
    onAccountSelected: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAccountName = accounts.find { it.id == selectedAccountId }?.name ?: "None"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.msg_choose_account_prefill),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedAccountName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                expanded = false
                                onAccountSelected(null)
                            }
                        )
                        HorizontalDivider()
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    expanded = false
                                    onAccountSelected(account.id)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
