package com.example.expensetracker.ui

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.expensetracker.MainActivity
import com.example.expensetracker.viewmodel.VoiceRecognitionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end integration tests for voice recognition functionality.
 * These tests run the full app with MainActivity and test voice recognition
 * behavior with various database states.
 */
@RunWith(AndroidJUnit4::class)
class VoiceRecognitionIntegrationTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var viewModel: com.example.expensetracker.viewmodel.ExpenseViewModel

    @Before
    fun setup() {
        composeTestRule.waitForIdle()
        
        // Get the ViewModel from the activity
        composeTestRule.activityRule.scenario.onActivity { activity ->
            viewModel = activity.viewModel
        }
        
        // Clear database to ensure we start with no accounts
        runBlocking {
            // Delete all data
            viewModel.allAccounts.value.forEach { account ->
                viewModel.deleteAccount(account)
            }
            viewModel.allExpenses.value.forEach { expense ->
                viewModel.deleteExpense(expense)
            }
            viewModel.allTransfers.value.forEach { transfer ->
                viewModel.deleteTransfer(transfer)
            }
        }
        
        // Wait for deletions to complete
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            viewModel.allAccounts.value.isEmpty()
        }
    }

    /**
     * Tests Bug Fix #1: Voice recognition for transfers should show error message
     * when no accounts exist, instead of hanging indefinitely.
     * 
     * Before fix: processParsedTransfer() used allAccounts.first { it.isNotEmpty() }
     *             which would suspend forever with empty accounts.
     * After fix:  Uses allAccounts.first() + isEmpty() check, returns error immediately.
     */
    @Test
    fun voiceTransfer_withNoAccounts_showsErrorInsteadOfHanging() {
        // GIVEN: No accounts exist (verified in setup)
        assertEquals("Database should be empty", 0, viewModel.allAccounts.value.size)
        
        // Initial state should be Idle
        assertTrue(
            "Initial voice state should be Idle",
            viewModel.voiceRecognitionState.value is VoiceRecognitionState.Idle
        )
        
        // WHEN: User attempts voice transfer with valid speech input
        composeTestRule.activityRule.scenario.onActivity {
            viewModel.onVoiceRecognitionResult("transfer from Wallet to Bank 100")
        }
        
        // THEN: Should receive error message immediately (not hang)
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            viewModel.voiceRecognitionState.value is VoiceRecognitionState.RecognitionFailed
        }
        
        val state = viewModel.voiceRecognitionState.value
        assertTrue(
            "Voice recognition should fail with RecognitionFailed state",
            state is VoiceRecognitionState.RecognitionFailed
        )
        
        val failedState = state as VoiceRecognitionState.RecognitionFailed
        assertTrue(
            "Error message should mention 'No accounts found'",
            failedState.message.contains("No accounts found") ||
            failedState.message.contains("no accounts", ignoreCase = true)
        )
        assertTrue(
            "Error message should suggest creating an account",
            failedState.message.contains("create", ignoreCase = true) &&
            failedState.message.contains("account", ignoreCase = true)
        )
    }

    /**
     * Tests that voice transfer works correctly after accounts are created.
     * This verifies the fix doesn't break the happy path.
     */
    @Test
    fun voiceTransfer_withAccounts_navigatesToEditScreen() {
        // GIVEN: Create two accounts
        composeTestRule.activityRule.scenario.onActivity {
            viewModel.insertAccount(
                com.example.expensetracker.data.Account(
                    name = "Wallet",
                    balance = java.math.BigDecimal("100.00"),
                    currency = "USD"
                )
            )
            viewModel.insertAccount(
                com.example.expensetracker.data.Account(
                    name = "Bank",
                    balance = java.math.BigDecimal("500.00"),
                    currency = "USD"
                )
            )
        }
        
        // Wait for accounts to be inserted
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            viewModel.allAccounts.value.size == 2
        }
        
        // WHEN: User attempts voice transfer
        composeTestRule.activityRule.scenario.onActivity {
            viewModel.onVoiceRecognitionResult("transfer from Wallet to Bank 100")
        }
        
        // THEN: Should navigate to EditTransferScreen (not fail)
        // The navigation happens via the _navigateTo channel in the ViewModel
        // We can verify by checking the voice state doesn't show an error
        composeTestRule.waitForIdle()
        
        val state = viewModel.voiceRecognitionState.value
        assertFalse(
            "Voice recognition should not fail when accounts exist",
            state is VoiceRecognitionState.RecognitionFailed
        )
    }

    /**
     * Tests that voice expense navigates to edit screen even with no accounts.
     * Note: The expense flow behaves differently from transfers - it navigates to
     * the edit screen with error highlighting rather than completely failing.
     * This test verifies it doesn't hang and completes the navigation.
     */
    @Test
    fun voiceExpense_withNoAccounts_navigatesToEditScreen() {
        // GIVEN: No accounts exist (verified in setup)
        assertEquals("Database should be empty", 0, viewModel.allAccounts.value.size)
        
        // Categories should still exist (default category is pre-populated)
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            viewModel.allCategories.value.isNotEmpty()
        }
        
        // WHEN: User attempts voice expense
        composeTestRule.activityRule.scenario.onActivity {
            viewModel.onVoiceRecognitionResult("expense from Wallet 50 category Food")
        }
        
        // THEN: Should navigate to EditExpenseScreen (navigation happens even when account not found)
        // We verify navigation by checking if the EditExpense screen appears
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_DATE_FIELD)
                    .fetchSemanticsNode()
                true
            } catch (e: Exception) {
                false  
            }
        }
        
        // If we get here, navigation worked (no hanging)
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_DATE_FIELD).assertExists()
    }
}
