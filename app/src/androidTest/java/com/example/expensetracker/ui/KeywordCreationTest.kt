package com.example.expensetracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.Keyword
import com.example.expensetracker.ui.screens.content.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class KeywordCreationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun keywordCreation_hidesKeyboard_onConfirm() {
        var hideKeyboardCalled = false
        val state = EditExpenseState()

        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = emptyList(),
                categories = emptyList(),
                keywords = emptyList(),
                callbacks = EditExpenseCallbacks(
                    onCreateKeyword = { 1L }, // Mock success
                    onHideKeyboard = { hideKeyboardCalled = true }
                )
            )
        }

        // Open keyword dropdown
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_SEARCH).performClick()
        
        // Wait for dropdown
        composeTestRule.waitForIdle()

        // Click "Create New" (it appears when list is empty or matches query)
        // Since list is empty, "Create New" should be visible if we type something or if it's always there in the implementation?
        // Checking implementation: "Create New..." option is always the first item in the dropdown.
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_CREATE_NEW).performClick()

        // Dialog should appear
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_NEW_NAME).assertExists()

        // Type new keyword name
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_NEW_NAME).performTextInput("New Keyword")

        // Click Confirm
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_CREATE_CONFIRM).performClick()

        // Verify hideKeyboard was called
        assertTrue("onHideKeyboard should be called after creating keyword", hideKeyboardCalled)
    }
}
