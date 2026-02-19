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
class CurrencyValidationTest {

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
    fun addCurrency_invalidValues_showsError() {
        // Navigate to Currencies
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.menu_desc)).performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.nav_currencies)).performClick()

        // Create New
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.option_create_new)).performClick()

        // Input Invalid Code
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_CODE_FIELD)
            .performTextInput("XYZ999")
            
        // Input Invalid Name
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_NAME_FIELD)
            .performTextInput("Fake Currency")

        // Click Save
        composeTestRule.onNodeWithTag(TestTags.ADD_CURRENCY_SAVE).performClick()

        // Verify Error
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.title_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.msg_invalid_currency)).assertIsDisplayed()
        
        // Dismiss
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.btn_ok)).performClick()
    }
}
