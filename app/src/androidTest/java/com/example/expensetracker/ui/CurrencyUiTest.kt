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
class CurrencyUiTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createCurrency_syncsCodeAndName_viaDropdown() {
        // Navigate to Currencies
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.onNodeWithText("Currencies").performClick()

        // Create New
        composeTestRule.onNodeWithText("Create New").performClick()

        // Verify we are on Add Currency screen
        composeTestRule.onNodeWithText("Add Currency").assertIsDisplayed()

        // Input "AUD" into Code field
        // Note: The field is inside an ExposedDropdownMenuBox. 
        // We use the test tag we added.
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_CODE_FIELD)
            .performTextInput("AUD")

        // The dropdown should appear. 
        // We look for the text "AUD - Australian Dollar" (or similar depending on Locale, but usually standard)
        // Adjust wait if necessary.
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("AUD - Australian Dollar").fetchSemanticsNodes().isNotEmpty()
        }

        // Click the dropdown item
        composeTestRule.onNodeWithText("AUD - Australian Dollar").performClick()

        // Verify Name field is updated automatically
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_NAME_FIELD)
            .assertTextContains("Australian Dollar")

        // Save
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_SAVE).performClick()

        // Verify we are back on list and item exists
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Australian Dollar (AUD)").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun editCurrency_fieldsAreReadOnly() {
        // Navigate to Currencies
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.onNodeWithText("Currencies").performClick()

        // We assume seeded data exists (USD, EUR, etc). 
        // Let's click on "United States Dollar (USD)" or similar.
        // The list might need scrolling if many items, but default list is small.
        // Text is constructed as "${currency.name} (${currency.code})"
        
        // Wait for list to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("USD", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click on a currency row. We'll pick one that likely exists.
        // Warning: The exact text depends on Locale if we used java.util.Currency.
        // But "USD" code should be present.
        composeTestRule.onNodeWithText("USD", substring = true).performClick()

        // Verify Edit Currency title
        composeTestRule.onNodeWithText("Edit Currency").assertIsDisplayed()

        // Verify Code field is read-only (disabled)
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_CODE_FIELD)
            .assertIsNotEnabled()

        // Verify Name field is read-only (disabled)
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_NAME_FIELD)
            .assertIsNotEnabled()
    }
}
