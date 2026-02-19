package com.example.expensetracker.ui.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.expensetracker.R
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
                title = { Text(stringResource(R.string.title_recognition_failed)) },
                text = { Text(currentState.message) },
                confirmButton = { Button(onClick = { viewModel.dismissVoiceRecognitionDialog() }) { Text(stringResource(R.string.btn_ok)) } },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_RECOGNITION_FAILED)
            )
        }

        is VoiceRecognitionState.SameAccountTransfer -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissVoiceRecognitionDialog() },
                title = { Text(stringResource(R.string.title_invalid_transfer)) },
                text = { Text(currentState.message) },
                confirmButton = { Button(onClick = { viewModel.dismissVoiceRecognitionDialog() }) { Text(stringResource(R.string.btn_ok)) } },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_SAME_ACCOUNT)
            )
        }
        is VoiceRecognitionState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissVoiceRecognitionDialog() },
                title = { Text(stringResource(R.string.title_success)) },
                text = { Text(currentState.message) },
                confirmButton = { Button(onClick = { viewModel.dismissVoiceRecognitionDialog() }) { Text(stringResource(R.string.btn_ok)) } },
                modifier = Modifier.testTag(TestTags.VOICE_DIALOG_SUCCESS)
            )
        }
    }
}
