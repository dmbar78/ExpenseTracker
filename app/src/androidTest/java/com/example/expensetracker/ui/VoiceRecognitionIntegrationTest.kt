package com.example.expensetracker.ui

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.expensetracker.MainActivity
import com.example.expensetracker.R
import com.example.expensetracker.viewmodel.VoiceRecognitionState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class VoiceRecognitionIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var viewModel: com.example.expensetracker.viewmodel.ExpenseViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        composeTestRule.waitForIdle()
        
        // Get the ViewModel from the activity
        composeTestRule.activityRule.scenario.onActivity { activity ->
            viewModel = activity.viewModel
        }
        
        // Clear database directly to ensure we start with no accounts
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = com.example.expensetracker.data.AppDatabase.getDatabase(context)
        
        runBlocking {
            db.expenseDao().deleteAll()
            db.transferHistoryDao().deleteAll()
            db.accountDao().deleteAll()
            // We can leave categories or defaults if needed, but for safety:
            // db.categoryDao().deleteAll() 
            // Note: If app requires default categories, we might need to re-insert or carefully delete.
            // Voice tests depend on "Food" category in one test case.
        }
        
        // Give ViewModel StateFlows time to initialize and collect from the database
        // After Hilt migration, StateFlows need a moment to collect their initial values
        Thread.sleep(300)
        composeTestRule.waitForIdle()
        
        // Verify state is ready (with increased timeout for Hilt initialization)
        composeTestRule.waitUntil(timeoutMillis = 10000) {
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
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expectedMessage = context.getString(R.string.err_voice_no_accounts)
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
            "Error message should match localized no-accounts text",
            failedState.message == expectedMessage
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

    @Test
    fun voiceExpense_capitalizesParsedInputs() {
        // GIVEN: No accounts exist
        assertEquals("Database should be empty", 0, viewModel.allAccounts.value.size)
        // Ensure categories exist
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            viewModel.allCategories.value.isNotEmpty()
        }

        // WHEN: User attempts voice expense with lowercase inputs
        composeTestRule.activityRule.scenario.onActivity {
            // "wallet" -> "Wallet", "food" -> "Food"
            viewModel.onVoiceRecognitionResult("expense from wallet 50 category food")
        }

        // THEN: Navigate to EditExpenseScreen and fields should be capitalized
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_VALUE)
                    .assertExists()
                true
            } catch (e: Exception) { false }
        }

        // Check Account Field Text
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_VALUE)
            .assertTextContains("Wallet") // Capitalized

        // Check Category Field Text
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_VALUE)
            .assertTextContains("Food") // Capitalized
    }
}
