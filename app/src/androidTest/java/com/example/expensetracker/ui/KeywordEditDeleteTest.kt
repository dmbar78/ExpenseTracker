package com.example.expensetracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.Keyword
import com.example.expensetracker.ui.screens.content.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeywordEditDeleteTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun keyword_edit_flow() {
        var editedKeyword: Keyword? = null
        val testKeyword = Keyword(id = 1, name = "TestKeyword")
        val state = EditExpenseState(
            selectedKeywordIds = setOf(1)
        )
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = emptyList(),
                categories = emptyList(),
                keywords = listOf(testKeyword),
                callbacks = EditExpenseCallbacks(
                    onEditKeyword = { keyword -> editedKeyword = keyword }
                )
            )
        }

        // 1. Find the chip and Long Press it
        composeTestRule.onNodeWithTag("KeywordChip").performTouchInput {
             longClick()
        }
        
        // 2. Click "Edit" in the dropdown menu
        composeTestRule.onNodeWithTag("EditMenuItem").performClick()
        
        // 3. Verify Dialog appears and enter new text
        composeTestRule.onNodeWithTag("EditKeywordName").performTextReplacement("UpdatedKeyword")
        
        // 4. Click Save (using test tag)
        composeTestRule.onNodeWithTag("EditKeywordSaveButton").performClick()
        
        // 5. Verify callback
        assertNotNull("onEditKeyword should be called", editedKeyword)
        assertEquals("UpdatedKeyword", editedKeyword?.name)
        assertEquals(1, editedKeyword?.id)
    }

    @Test
    fun keyword_delete_flow() {
        var deletedKeyword: Keyword? = null
        val testKeyword = Keyword(id = 1, name = "TestKeyword")
        val state = EditExpenseState(
            selectedKeywordIds = setOf(1)
        )
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = emptyList(),
                categories = emptyList(),
                keywords = listOf(testKeyword),
                callbacks = EditExpenseCallbacks(
                    onDeleteKeyword = { keyword -> deletedKeyword = keyword }
                )
            )
        }

        // 1. Find the chip and Long Press it
        composeTestRule.onNodeWithTag("KeywordChip").performTouchInput {
             longClick()
        }
        
        composeTestRule.waitForIdle()
        
        // 2. Click "Delete" in the dropdown menu
        composeTestRule.onNodeWithTag("DeleteMenuItem").performClick()
        
        // 3. Verify Confirmation Dialog appears
        composeTestRule.onNodeWithText("Delete Keyword").assertExists()
        
        // 4. Click Delete (using test tag)
        composeTestRule.onNodeWithTag("DeleteKeywordConfirmButton").performClick()
        
        // 5. Verify callback
        assertNotNull("onDeleteKeyword should be called", deletedKeyword)
        assertEquals(1, deletedKeyword?.id)
    }
}
