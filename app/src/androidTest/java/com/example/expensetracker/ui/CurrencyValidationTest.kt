package com.example.expensetracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.expensetracker.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrencyValidationTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun addCurrency_invalidValues_showsError() {
        // Navigate to Currencies
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.onNodeWithText("Currencies").performClick()

        // Create New
        composeTestRule.onNodeWithText("Create New").performClick()

        // Input Invalid Code
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_CODE_FIELD)
            .performTextInput("XYZ999")
            
        // Input Invalid Name
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_NAME_FIELD)
            .performTextInput("Fake Currency")

        // Click Save
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_SAVE).performClick()

        // Verify Error
        composeTestRule.onNodeWithText("Error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Invalid currency. Please select a valid currency (Code and Name) from the list.").assertIsDisplayed()
        
        // Dismiss
        composeTestRule.onNodeWithText("OK").performClick()
    }
}
