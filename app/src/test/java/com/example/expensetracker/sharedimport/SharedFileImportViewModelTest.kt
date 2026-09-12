package com.example.expensetracker.sharedimport

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class SharedFileImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var processor: FakeProcessor

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        processor = FakeProcessor()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful import becomes ready and duplicate event is ignored`() = runTest(dispatcher) {
        val viewModel = SharedFileImportViewModel(SavedStateHandle(), processor)
        val uri = mock(Uri::class.java)

        viewModel.handleSharedFile(uri, "image/jpeg", "event-1")
        advanceUntilIdle()
        viewModel.handleSharedFile(uri, "image/jpeg", "event-1")
        advanceUntilIdle()

        assertEquals(1, processor.processCount)
        assertEquals(processor.result.amount, (viewModel.state.value as SharedFileImportState.Ready).amount)
    }

    @Test
    fun `processor error becomes user facing error state`() = runTest(dispatcher) {
        processor.failure = SharedFileImportException(42)
        val viewModel = SharedFileImportViewModel(SavedStateHandle(), processor)

        viewModel.handleSharedFile(mock(Uri::class.java), "application/pdf", "event-2")
        advanceUntilIdle()

        assertEquals(SharedFileImportState.Error(42), viewModel.state.value)
    }

    @Test
    fun `cancel deletes staged ready file`() = runTest(dispatcher) {
        val viewModel = SharedFileImportViewModel(SavedStateHandle(), processor)
        viewModel.handleSharedFile(mock(Uri::class.java), "image/png", "event-3")
        advanceUntilIdle()

        viewModel.cancel()

        assertEquals(listOf("C:/cache/shared.jpg"), processor.deletedPaths)
        assertTrue(viewModel.state.value is SharedFileImportState.Idle)
    }

    @Test
    fun `ready primitives restore from saved state`() {
        val handle = SavedStateHandle(
            mapOf(
                "shared_import_state" to "ready",
                "shared_import_amount" to "25.50",
                "shared_import_date" to 1234L,
                "shared_import_path" to "C:/cache/shared.jpg",
                "shared_import_mime" to "image/jpeg"
            )
        )

        val state = SharedFileImportViewModel(handle, processor).state.value

        assertEquals(SharedFileImportState.Ready("25.50", 1234L, "C:/cache/shared.jpg", "image/jpeg"), state)
    }

    private class FakeProcessor : SharedFileImportProcessor {
        var processCount = 0
        var failure: SharedFileImportException? = null
        val deletedPaths = mutableListOf<String>()
        val result = ProcessedSharedFile("25.50", 1234L, "C:/cache/shared.jpg", "image/jpeg")

        override suspend fun process(uri: Uri, declaredMimeType: String?): ProcessedSharedFile {
            processCount++
            failure?.let { throw it }
            return result
        }

        override fun deleteStagedFile(path: String) {
            deletedPaths += path
        }
    }
}