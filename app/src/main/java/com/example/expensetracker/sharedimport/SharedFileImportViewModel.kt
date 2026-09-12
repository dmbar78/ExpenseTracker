package com.example.expensetracker.sharedimport

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SharedFileImportViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val processor: SharedFileImportProcessor
) : ViewModel() {
    private val _state = MutableStateFlow(restoreState())
    val state: StateFlow<SharedFileImportState> = _state.asStateFlow()
    private var processingJob: Job? = null

    fun handleIntent(intent: Intent, eventId: String) {
        if (intent.action != Intent.ACTION_SEND || savedStateHandle.get<String>(KEY_EVENT_ID) == eventId) return
        val uri: Uri? = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (uri == null) {
            savedStateHandle[KEY_EVENT_ID] = eventId
            setError(R.string.err_shared_file_unreadable)
            return
        }
        handleSharedFile(uri, intent.type, eventId)
    }

    internal fun handleSharedFile(uri: Uri, mimeType: String?, eventId: String) {
        if (savedStateHandle.get<String>(KEY_EVENT_ID) == eventId) return
        processingJob?.cancel()
        (_state.value as? SharedFileImportState.Ready)?.let { processor.deleteStagedFile(it.stagedFilePath) }
        savedStateHandle[KEY_EVENT_ID] = eventId
        setState(SharedFileImportState.Processing)
        processingJob = viewModelScope.launch {
            try {
                val result = processor.process(uri, mimeType)
                if (savedStateHandle.get<String>(KEY_EVENT_ID) == eventId) {
                    setState(
                        SharedFileImportState.Ready(
                            result.amount,
                            result.dateMillis,
                            result.stagedFilePath,
                            result.mimeType
                        )
                    )
                } else {
                    processor.deleteStagedFile(result.stagedFilePath)
                }
            } catch (error: SharedFileImportException) {
                if (savedStateHandle.get<String>(KEY_EVENT_ID) == eventId) setError(error.messageResId)
            }
        }
    }

    fun consume(deleteStagedFile: Boolean): SharedFileImportState.Ready? {
        val ready = _state.value as? SharedFileImportState.Ready ?: return null
        if (deleteStagedFile) processor.deleteStagedFile(ready.stagedFilePath)
        clearState()
        return ready
    }

    fun cancel() {
        processingJob?.cancel()
        (_state.value as? SharedFileImportState.Ready)?.let { processor.deleteStagedFile(it.stagedFilePath) }
        clearState()
    }

    fun acknowledgeError() = clearState()

    private fun setError(messageResId: Int) = setState(SharedFileImportState.Error(messageResId))

    private fun setState(state: SharedFileImportState) {
        _state.value = state
        savedStateHandle[KEY_STATE] = when (state) {
            SharedFileImportState.Idle -> STATE_IDLE
            SharedFileImportState.Processing -> STATE_PROCESSING
            is SharedFileImportState.Ready -> STATE_READY
            is SharedFileImportState.Error -> STATE_ERROR
        }
        if (state is SharedFileImportState.Ready) {
            savedStateHandle[KEY_AMOUNT] = state.amount
            savedStateHandle[KEY_DATE] = state.dateMillis
            savedStateHandle[KEY_PATH] = state.stagedFilePath
            savedStateHandle[KEY_MIME] = state.mimeType
        }
        if (state is SharedFileImportState.Error) savedStateHandle[KEY_ERROR] = state.messageResId
    }

    private fun clearState() {
        listOf(KEY_STATE, KEY_AMOUNT, KEY_DATE, KEY_PATH, KEY_MIME, KEY_ERROR).forEach { key ->
            savedStateHandle.remove<Any>(key)
        }
        _state.value = SharedFileImportState.Idle
    }

    private fun restoreState(): SharedFileImportState = when (savedStateHandle.get<String>(KEY_STATE)) {
        STATE_READY -> {
            val path = savedStateHandle.get<String>(KEY_PATH)
            val mime = savedStateHandle.get<String>(KEY_MIME)
            val date = savedStateHandle.get<Long>(KEY_DATE)
            if (path != null && mime != null && date != null) {
                SharedFileImportState.Ready(savedStateHandle.get<String>(KEY_AMOUNT), date, path, mime)
            } else SharedFileImportState.Idle
        }
        STATE_ERROR -> SharedFileImportState.Error(
            savedStateHandle.get<Int>(KEY_ERROR) ?: R.string.err_shared_file_processing
        )
        else -> SharedFileImportState.Idle
    }

    private companion object {
        const val KEY_EVENT_ID = "shared_import_event_id"
        const val KEY_STATE = "shared_import_state"
        const val KEY_AMOUNT = "shared_import_amount"
        const val KEY_DATE = "shared_import_date"
        const val KEY_PATH = "shared_import_path"
        const val KEY_MIME = "shared_import_mime"
        const val KEY_ERROR = "shared_import_error"
        const val STATE_IDLE = "idle"
        const val STATE_PROCESSING = "processing"
        const val STATE_READY = "ready"
        const val STATE_ERROR = "error"
    }
}