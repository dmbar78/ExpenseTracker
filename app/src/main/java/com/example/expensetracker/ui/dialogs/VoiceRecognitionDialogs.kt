package com.example.expensetracker.ui.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.expensetracker.ui.TestTags
import com.example.expensetracker.viewmodel.ExpenseViewModel
import com.example.expensetracker.viewmodel.VoiceRecognitionState

@Composable
fun VoiceRecognitionDialogs(viewModel: ExpenseViewModel) {
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
