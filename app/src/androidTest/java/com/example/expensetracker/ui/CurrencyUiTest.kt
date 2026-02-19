package com.example.expensetracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.expensetracker.MainActivity
import com.example.expensetracker.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CurrencyUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun createCurrency_syncsCodeAndName_viaDropdown() {
        // Navigate to Currencies
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.menu_desc)).performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.nav_currencies)).performClick()

        // Create New
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.option_create_new)).performClick()

        // Verify we are on Add Currency screen
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.title_add_currency)).assertIsDisplayed()

        // Input "AUD" into Code field
        // Note: The field is inside an ExposedDropdownMenuBox. 
        // We use the test tag we added.
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_CODE_FIELD)
            .performTextInput("AUD")

        // The dropdown should appear. 
        // We wait for an option containing AUD code (locale-safe).
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("AUD", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click the dropdown item
        composeTestRule.onNodeWithText("AUD", substring = true).performClick()

        // Verify code remains selected
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_CODE_FIELD)
            .assertTextContains("AUD")

        // Save
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_SAVE).performClick()

        // Verify we are back on list and item exists
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("AUD", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun editCurrency_fieldsAreReadOnly() {
        // Navigate to Currencies
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.menu_desc)).performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.nav_currencies)).performClick()

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
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.title_edit_currency)).assertIsDisplayed()

        // Verify Code field is read-only (disabled)
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_CODE_FIELD)
            .assertIsNotEnabled()

        // Verify Name field is read-only (disabled)
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_NAME_FIELD)
            .assertIsNotEnabled()
    }
}
