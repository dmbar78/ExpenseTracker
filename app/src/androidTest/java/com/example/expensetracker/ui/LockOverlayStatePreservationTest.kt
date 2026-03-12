package com.example.expensetracker.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.MainActivity
import com.example.expensetracker.R
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.Keyword
import com.example.expensetracker.data.SecurityManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LockOverlayStatePreservationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val permissionRule: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(context)

        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        SecurityManager.setLocked()

        runBlocking {
            db.debtDao().deleteAll()
            db.expenseDao().deleteAll()
            db.transferHistoryDao().deleteAll()
            db.accountDao().deleteAll()
            db.categoryDao().deleteAll()
            db.keywordDao().deleteAllCrossRefs()
            db.keywordDao().deleteAllKeywords()
        }
    }

    @Test
    fun lockOverlay_preservesCurrentAccountsRoute() {
        val menuDesc = context.getString(R.string.menu_desc)
        val accountsText = context.getString(R.string.nav_accounts)

        composeTestRule.onNodeWithContentDescription(menuDesc).performClick()
        composeTestRule.onNodeWithText(accountsText).performClick()
        composeTestRule.onNodeWithTag(TestTags.ACCOUNTS_ROOT).assertIsDisplayed()

        setPinAndTriggerLock()

        composeTestRule.onNodeWithTag(TestTags.PIN_LOCK_ROOT).assertIsDisplayed()
        enterPin("123456")

        composeTestRule.onNodeWithTag(TestTags.ACCOUNTS_ROOT).assertIsDisplayed()
    }

    @Test
    fun editExpenseDraft_survivesRecreation() {
        runBlocking {
            db.accountDao().insert(Account(name = "Wallet", currency = "USD", balance = BigDecimal("100.00")))
        }
        val keywordId = runBlocking { db.keywordDao().insert(Keyword(name = "Urgent")) }

        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_EXPENSE).performClick()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ROOT).assertIsDisplayed()

        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_VALUE).performClick()
        composeTestRule.onNodeWithText("Wallet").performClick()

        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD).performTextInput("123.45")
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_VALUE).performTextInput("Taxi")
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_COMMENT_FIELD).performTextInput("Draft should survive")

        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_SEARCH).performClick()
        composeTestRule.onNodeWithText("Urgent").performClick()
        composeTestRule.onNodeWithTag(TestTags.KEYWORD_CHIP_PREFIX + keywordId).assertIsDisplayed()

        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ADD_PHOTO_BUTTON).performClick()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ADD_PHOTO_DIALOG).assertIsDisplayed()

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ROOT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD).assertTextContains("123.45")
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_VALUE).assertTextContains("Taxi")
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_COMMENT_FIELD).assertTextContains("Draft should survive")
        composeTestRule.onNodeWithTag(TestTags.KEYWORD_CHIP_PREFIX + keywordId).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ADD_PHOTO_DIALOG).assertIsDisplayed()
    }

    @Test
    fun editTransferDraft_survivesRecreation() {
        runBlocking {
            db.accountDao().insert(Account(name = "Checking", currency = "USD", balance = BigDecimal("500.00")))
        }
        runBlocking {
            db.accountDao().insert(Account(name = "Savings", currency = "USD", balance = BigDecimal("300.00")))
        }

        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_TRANSFER).performClick()
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_ROOT).assertIsDisplayed()

        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SOURCE_VALUE).performClick()
        composeTestRule.onNodeWithText("Checking").performClick()

        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DESTINATION_VALUE).performClick()
        composeTestRule.onNodeWithText("Savings").performClick()

        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD).performTextInput("77.77")
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_COMMENT_FIELD).performTextInput("Transfer draft")

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_ROOT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SOURCE_VALUE).assertTextContains("Checking")
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DESTINATION_VALUE).assertTextContains("Savings")
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD).assertTextContains("77.77")
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_COMMENT_FIELD).assertTextContains("Transfer draft")
    }

    private fun setPinAndTriggerLock() {
        SecurityManager.setPin(context, "123456")
        SecurityManager.setLocked()
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.waitForIdle()
    }

    private fun enterPin(pin: String) {
        pin.forEach { char ->
            composeTestRule.onNodeWithTag(TestTags.PIN_KEY_PREFIX + char).performClick()
        }
        composeTestRule.waitForIdle()
    }
}