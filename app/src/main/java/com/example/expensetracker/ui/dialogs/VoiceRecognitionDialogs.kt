package com.example.expensetracker.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.TestTags
import androidx.navigation.NavController
import com.example.expensetracker.data.Account
import com.example.expensetracker.viewmodel.ExpenseViewModel
import com.example.expensetracker.viewmodel.VoiceRecognitionState

@Composable
fun VoiceRecognitionDialogs(viewModel: ExpenseViewModel, navController: NavController) {
    val state by viewModel.voiceRecognitionState.collectAsState()

    when (val currentState = state) {
        is VoiceRecognitionState.Idle -> {}
        is VoiceRecognitionState.RecognitionFailed -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissVoiceRecognitionDialog() },
                title = { Text("Recognition Failed") },
                text = { Text(currentState.message) },
                confirmButton = { Button(onClick = { viewModel.dismissVoiceRecognitionDialog() }) { Text("OK") } },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_RECOGNITION_FAILED)
            )
        }
        is VoiceRecognitionState.TransferAccountsNotFound -> {
            TransferAccountDisambiguationDialog(
                viewModel = viewModel,
                parsedTransfer = currentState.parsedTransfer,
                accounts = currentState.availableAccounts
            )
        }
        is VoiceRecognitionState.TransferCurrencyMismatch -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissVoiceRecognitionDialog() },
                title = { Text("Currency Mismatch") },
                text = { Text(currentState.message) },
                confirmButton = { Button(onClick = { viewModel.dismissVoiceRecognitionDialog() }) { Text("OK") } },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_CURRENCY_MISMATCH)
            )
        }
        is VoiceRecognitionState.SameAccountTransfer -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissVoiceRecognitionDialog() },
                title = { Text("Invalid Transfer") },
                text = { Text(currentState.message) },
                confirmButton = { Button(onClick = { viewModel.dismissVoiceRecognitionDialog() }) { Text("OK") } },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_SAME_ACCOUNT)
            )
        }
        is VoiceRecognitionState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissVoiceRecognitionDialog() },
                title = { Text("Success") },
                text = { Text(currentState.message) },
                confirmButton = { Button(onClick = { viewModel.dismissVoiceRecognitionDialog() }) { Text("OK") } },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_SUCCESS)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferAccountDisambiguationDialog(
    viewModel: ExpenseViewModel,
    parsedTransfer: com.example.expensetracker.viewmodel.ParsedTransfer,
    accounts: List<Account>
) {
    var selectedSourceAccount by remember { mutableStateOf<Account?>(null) }
    var selectedDestAccount by remember { mutableStateOf<Account?>(null) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var destExpanded by remember { mutableStateOf(false) }

    val sourceNotFound = accounts.find { it.name.equals(parsedTransfer.sourceAccountName, ignoreCase = true) } == null
    val destNotFound = accounts.find { it.name.equals(parsedTransfer.destAccountName, ignoreCase = true) } == null

    AlertDialog(
        onDismissRequest = { viewModel.dismissVoiceRecognitionDialog() },
        title = { Text("Account Not Found") },
        text = {
            Column {
                if (sourceNotFound) {
                    Text(
                        "Couldn't find source account '${parsedTransfer.sourceAccountName}' in database. Please select the required account from the list.",
                        modifier = Modifier.testTag(TestTags.VOICE_DIALOG_ERROR_SOURCE_NOT_FOUND)
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = sourceExpanded,
                    onExpandedChange = { sourceExpanded = !sourceExpanded },
                    modifier = Modifier.testTag(TestTags.VOICE_DIALOG_SOURCE_DROPDOWN)
                ) {
                    OutlinedTextField(
                        value = selectedSourceAccount?.name ?: parsedTransfer.sourceAccountName,
                        onValueChange = {},
                        label = { Text("Source Account") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                        modifier = Modifier.menuAnchor().testTag(TestTags.VOICE_DIALOG_SOURCE_VALUE)
                    )
                    ExposedDropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedSourceAccount = account
                                    sourceExpanded = false
                                },
                                modifier = Modifier.testTag(TestTags.ACCOUNT_OPTION_PREFIX + "dialog_source_" + account.id)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (destNotFound) {
                    Text(
                        "Couldn't find destination account '${parsedTransfer.destAccountName}' in database. Please select the required account from the list.",
                        modifier = Modifier.testTag(TestTags.VOICE_DIALOG_ERROR_DEST_NOT_FOUND)
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = destExpanded,
                    onExpandedChange = { destExpanded = !destExpanded },
                    modifier = Modifier.testTag(TestTags.VOICE_DIALOG_DESTINATION_DROPDOWN)
                ) {
                    OutlinedTextField(
                        value = selectedDestAccount?.name ?: parsedTransfer.destAccountName,
                        onValueChange = {},
                        label = { Text("Destination Account") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destExpanded) },
                        modifier = Modifier.menuAnchor().testTag(TestTags.VOICE_DIALOG_DESTINATION_VALUE)
                    )
                    ExposedDropdownMenu(expanded = destExpanded, onDismissRequest = { destExpanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedDestAccount = account
                                    destExpanded = false
                                },
                                modifier = Modifier.testTag(TestTags.ACCOUNT_OPTION_PREFIX + "dialog_dest_" + account.id)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val source = selectedSourceAccount?.name ?: parsedTransfer.sourceAccountName
                    val dest = selectedDestAccount?.name ?: parsedTransfer.destAccountName
                    viewModel.reprocessTransfer(parsedTransfer.copy(sourceAccountName = source, destAccountName = dest))
                },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_SAVE)
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(
                onClick = { viewModel.dismissVoiceRecognitionDialog() },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_CANCEL)
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag(TestTags.VOICE_DIALOG_TRANSFER_ACCOUNTS_NOT_FOUND)
    )
}
