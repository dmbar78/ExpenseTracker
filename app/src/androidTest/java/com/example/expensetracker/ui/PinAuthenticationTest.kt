package com.example.expensetracker.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinAuthenticationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @get:Rule
    val permissionRule: androidx.test.rule.GrantPermissionRule = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)
    
    @Before
    fun setup() {
        // Clear Prefs to start clean
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }
    
    @Test
    fun testPinLifecycle() {
        // Navigate to Settings
        // Open Navigation Drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        
        // Click Settings
        composeTestRule.onNodeWithText("Settings").performClick()
        
        // 1. Create PIN
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_ADD_PIN).performClick()
        
        enterPin("123456") // Create
        enterPin("123456") // Confirm
        
        // Verify PIN is set (Change PIN button visible)
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CHANGE_PIN).assertIsDisplayed()
        
        /*
        // 2. Lockout Test
        // Go to Change PIN to test verify logic (which uses verification first)
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CHANGE_PIN).performClick()
        
        // Enter wrong pin 5 times
        // Enter wrong pin 6 times to be sure
        repeat(6) {
             enterPin("111111")
             composeTestRule.waitForIdle()
             Thread.sleep(200)
        }
        
        // Check for lockout message
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithTag(TestTags.PIN_LOCK_INSTRUCTION).assertTextContains("Too many attempts")
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        // Reset Lockout Manually for next steps
        resetLockout()
        
        // Close Pin Screen (Cancel) to reset state
        composeTestRule.onNodeWithTag(TestTags.PIN_LOCK_CANCEL).performClick()
        */
        
        // 3. Change PIN
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CHANGE_PIN).performClick()
        enterPin("123456") // Old
        enterPin("654321") // New
        enterPin("654321") // Confirm
        
        // Verify success (Dialog closes, we are back in settings)
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CHANGE_PIN).assertIsDisplayed()
        
        // 4. Remove PIN
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_REMOVE_PIN).performClick()
        // Now requires PIN entry
        enterPin("654321") // We changed it to this
        
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_ADD_PIN).assertIsDisplayed()
        
        // 5. Restart and Verify No Lock
        // Trigger activity recreation
        composeTestRule.activityRule.scenario.recreate()
        // Check we are NOT in lock screen
        composeTestRule.onNodeWithTag(TestTags.PIN_LOCK_ROOT).assertDoesNotExist()
    }
    
    private fun enterPin(pin: String) {
        pin.forEach { char ->
            composeTestRule.onNodeWithTag(TestTags.PIN_KEY_PREFIX + char).performClick()
        }
    }
    
    private fun resetLockout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit()
            .remove("failed_attempts")
            .remove("lockout_timestamp")
            .commit()
    }
}
