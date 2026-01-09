package com.example.expensetracker.ui.screens.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.Account
import com.example.expensetracker.ui.TestTags
import com.example.expensetracker.viewmodel.ParsedTransfer

/**
 * State holder for TransferAccountDisambiguationDialog content.
 */
data class TransferDisambiguationState(
    val parsedTransfer: ParsedTransfer,
    val accounts: List<Account>,
    val sourceNotFound: Boolean,
    val destNotFound: Boolean
)

/**
 * Callbacks for TransferAccountDisambiguationDialog content.
 */
data class TransferDisambiguationCallbacks(
    val onSourceAccountSelect: (Account) -> Unit = {},
    val onDestAccountSelect: (Account) -> Unit = {},
    val onSave: (sourceAccountName: String, destAccountName: String) -> Unit = { _, _ -> },
    val onCancel: () -> Unit = {}
)

/**
 * Pure UI content composable for the Transfer Account Disambiguation Dialog.
 * Accepts state + callbacks, no ViewModel dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDisambiguationDialogContent(
    state: TransferDisambiguationState,
    callbacks: TransferDisambiguationCallbacks,
    modifier: Modifier = Modifier
) {
    var selectedSourceAccount by remember { mutableStateOf<Account?>(null) }
    var selectedDestAccount by remember { mutableStateOf<Account?>(null) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var destExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { callbacks.onCancel() },
        title = { Text("Account Not Found") },
        text = {
            Column {
                if (state.sourceNotFound) {
                    Text(
                        "Couldn't find source account '${state.parsedTransfer.sourceAccountName}' in database. Please select the required account from the list.",
                        modifier = Modifier.testTag(TestTags.VOICE_DIALOG_ERROR_SOURCE_NOT_FOUND)
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = sourceExpanded,
                    onExpandedChange = { sourceExpanded = !sourceExpanded },
                    modifier = Modifier.testTag(TestTags.VOICE_DIALOG_SOURCE_DROPDOWN)
                ) {
                    OutlinedTextField(
                        value = selectedSourceAccount?.name ?: state.parsedTransfer.sourceAccountName,
                        onValueChange = {},
                        label = { Text("Source Account") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                        modifier = Modifier.menuAnchor().testTag(TestTags.VOICE_DIALOG_SOURCE_VALUE)
                    )
                    ExposedDropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                        state.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedSourceAccount = account
                                    sourceExpanded = false
                                    callbacks.onSourceAccountSelect(account)
                                },
                                modifier = Modifier.testTag(TestTags.ACCOUNT_OPTION_PREFIX + "dialog_source_" + account.id)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (state.destNotFound) {
                    Text(
                        "Couldn't find destination account '${state.parsedTransfer.destAccountName}' in database. Please select the required account from the list.",
                        modifier = Modifier.testTag(TestTags.VOICE_DIALOG_ERROR_DEST_NOT_FOUND)
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = destExpanded,
                    onExpandedChange = { destExpanded = !destExpanded },
                    modifier = Modifier.testTag(TestTags.VOICE_DIALOG_DESTINATION_DROPDOWN)
                ) {
                    OutlinedTextField(
                        value = selectedDestAccount?.name ?: state.parsedTransfer.destAccountName,
                        onValueChange = {},
                        label = { Text("Destination Account") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destExpanded) },
                        modifier = Modifier.menuAnchor().testTag(TestTags.VOICE_DIALOG_DESTINATION_VALUE)
                    )
                    ExposedDropdownMenu(expanded = destExpanded, onDismissRequest = { destExpanded = false }) {
                        state.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedDestAccount = account
                                    destExpanded = false
                                    callbacks.onDestAccountSelect(account)
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
                    val source = selectedSourceAccount?.name ?: state.parsedTransfer.sourceAccountName
                    val dest = selectedDestAccount?.name ?: state.parsedTransfer.destAccountName
                    callbacks.onSave(source, dest)
                },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_SAVE)
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(
                onClick = { callbacks.onCancel() },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_CANCEL)
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier.testTag(TestTags.VOICE_DIALOG_TRANSFER_ACCOUNTS_NOT_FOUND)
    )
}
