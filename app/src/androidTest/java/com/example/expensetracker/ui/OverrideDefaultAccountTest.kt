package com.example.expensetracker.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.MainActivity
import com.example.expensetracker.R
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.SecurityManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Instrumented UI tests for the "Override default expense/income account value if account filter
 * enabled" feature.
 *
 * Covers the following manual verification scenarios:
 *  A. Checkbox Disabled, Default="Cash", Filter="Bank"  -> Prefill="Cash"
 *  B. Checkbox Enabled,  Default="Cash", Filter="Bank"  -> Prefill="Bank"
 *  C. Checkbox Disabled, Default=None,   Filter="Bank"  -> No prefill (empty account field)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OverrideDefaultAccountTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Pre-grant microphone permission so the system dialog does not block the UI.
    @get:Rule(order = 1)
    val permissionRule: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val ctx: Context by lazy {
        androidx.test.core.app.ApplicationProvider.getApplicationContext()
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    @Before
    fun init() {
        hiltRule.inject()

        // Clear any PIN / lock so the PIN screen is never shown during tests.
        ctx.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        SecurityManager.setLocked() // needs to be re-checked; setLocked without pin means unlocked

        val db = AppDatabase.getDatabase(ctx)
        runBlocking {
            db.debtDao().deleteAll()
            db.expenseDao().deleteAll()
            db.transferHistoryDao().deleteAll()
            db.accountDao().deleteAll()
            db.categoryDao().deleteAll()
            db.currencyDao().deleteAll()
            db.currencyDao().insert(com.example.expensetracker.data.Currency(code = "USD", name = "United States Dollar"))
            db.categoryDao().insert(com.example.expensetracker.data.Category(name = "General"))
            db.accountDao().insert(Account(name = "Cash", currency = "USD", balance = BigDecimal("100.00")))
            db.accountDao().insert(Account(name = "Bank", currency = "USD", balance = BigDecimal("200.00")))
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun waitForMenu() {
        val menuDesc = ctx.getString(R.string.menu_desc)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeTestRule.onAllNodesWithContentDescription(menuDesc).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    /** Open the navigation drawer then tap Settings. */
    private fun openSettings() {
        val menuDesc = ctx.getString(R.string.menu_desc)
        composeTestRule.onNodeWithContentDescription(menuDesc).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(ctx.getString(R.string.nav_settings)).performClick()
        composeTestRule.waitForIdle()
    }

    /** Navigate back using the device back button. */
    private fun goBack() {
        Espresso.pressBack()
        composeTestRule.waitForIdle()
    }

    /** Returns true when the Override checkbox in Settings is currently checked. */
    private fun isOverrideCheckboxChecked(): Boolean {
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onAllNodesWithTag(TestTags.OVERRIDE_DEFAULT_ACCOUNT_CHECKBOX).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        val toggleState = composeTestRule
            .onNodeWithTag(TestTags.OVERRIDE_DEFAULT_ACCOUNT_CHECKBOX)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState]
        return toggleState == androidx.compose.ui.state.ToggleableState.On
    }

    /** Set the Override checkbox to [desired] state. */
    private fun setOverrideCheckbox(desired: Boolean) {
        if (isOverrideCheckboxChecked() != desired) {
            composeTestRule.onNodeWithTag(TestTags.OVERRIDE_DEFAULT_ACCOUNT_CHECKBOX).performScrollTo().performClick()
            composeTestRule.waitForIdle()
        }
    }

    /**
     * Set the Default Expense Account by scrolling to the setting and picking [accountName].
     * Pass `null` to select "None".
     */
    private fun setDefaultExpenseAccount(accountName: String?) {
        val settingLabel = ctx.getString(R.string.title_default_expense_account)
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onAllNodesWithText(settingLabel).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText(settingLabel).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Open the dropdown menu inside the dialog
        composeTestRule.onNodeWithText("Account").performClick()
        composeTestRule.waitForIdle()

        if (accountName == null) {
            // Need to use ignoreCase or exact match. "None" is the text in the menu.
            composeTestRule.onNode(hasText("None") and hasAnyAncestor(isPopup())).performClick()
        } else {
            // The item text is just the account name
            composeTestRule
                .onNode(hasText(accountName, substring = true) and hasAnyAncestor(isPopup()))
                .performClick()
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Set the Expense/Income account filter using the FilterIconButton
     * -> Filter main menu -> "Expense/Income Account" -> dialog dropdown -> OK.
     */
    private fun setAccountFilter(accountName: String) {
        // Tap the filter icon (contentDescription "Filter" — hardcoded in FilterIconButton)
        composeTestRule.onNodeWithContentDescription("Filter").performClick()
        composeTestRule.waitForIdle()

        // Tap "Expense/Income Account" menu item
        composeTestRule
            .onNodeWithText(ctx.getString(R.string.lbl_filter_expense_income_account))
            .performClick()
        composeTestRule.waitForIdle()

        // Wait for the AccountFilterDialog to appear (its title is R.string.title_filter_account)
        val filterDialogTitle = ctx.getString(R.string.title_filter_account)
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onAllNodesWithText(filterDialogTitle).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        // Click the dropdown field using its test tag
        composeTestRule
            .onNodeWithTag(TestTags.FILTER_ACCOUNT_DROPDOWN)
            .performClick()
        composeTestRule.waitForIdle()

        // Now the dropdown is expanded. Select the target account from the popup.
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onAllNodes(
                    hasText(accountName, substring = true) and hasAnyAncestor(isPopup())
                ).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule
            .onNode(hasText(accountName, substring = true) and hasAnyAncestor(isPopup()))
            .performClick()
        composeTestRule.waitForIdle()

        // Confirm the dialog
        val okText = ctx.getString(R.string.btn_ok)
        composeTestRule.onNodeWithText(okText).performClick()
        composeTestRule.waitForIdle()
    }

    /** Tap the global "+" FAB, then select "Create Expense". */
    private fun openAddExpense() {
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_EXPENSE).performClick()
        composeTestRule.waitForIdle()
        // Wait until the Edit Expense screen is shown
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onAllNodesWithTag(TestTags.EDIT_EXPENSE_ROOT).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    /** Read the current value text of the account field on the EditExpense screen. */
    private fun getAccountFieldText(): String {
        return composeTestRule
            .onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_VALUE)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
            ?.text
            ?: ""
    }

    // ── Tests ────────────────────────────────────────────────────────────

    /**
     * Scenario A: Override OFF, Default="Cash", Filter="Bank" -> prefill should be "Cash".
     */
    @Test
    fun testScenarioA_checkboxDisabled_defaultCash_filterBank_prefillsCash() {
        waitForMenu()

        // Configure Settings: Override OFF, Default = "Cash"
        openSettings()
        setOverrideCheckbox(false)
        setDefaultExpenseAccount("Cash")
        goBack()

        // Set filter to "Bank"
        setAccountFilter("Bank")

        // Open Add Expense and check prefill
        openAddExpense()
        val accountText = getAccountFieldText()
        assert(accountText == "Cash") {
            "Expected account prefill 'Cash' but got '$accountText'"
        }

        goBack()
    }

    /**
     * Scenario B: Override ON, Default="Cash", Filter="Bank" -> prefill should be "Bank".
     */
    @Test
    fun testScenarioB_checkboxEnabled_defaultCash_filterBank_prefillsBank() {
        waitForMenu()

        // Configure Settings: Override ON, Default = "Cash"
        openSettings()
        setOverrideCheckbox(true)
        setDefaultExpenseAccount("Cash")
        goBack()

        // Set filter to "Bank"
        setAccountFilter("Bank")

        // Open Add Expense and check prefill
        openAddExpense()
        val accountText = getAccountFieldText()
        assert(accountText == "Bank") {
            "Expected account prefill 'Bank' but got '$accountText'"
        }

        goBack()

        // Cleanup: reset override back to false to avoid affecting other tests
        openSettings()
        setOverrideCheckbox(false)
        goBack()
    }

    /**
     * Scenario C: Override OFF, Default=None, Filter="Bank" -> no prefill (blank account).
     */
    @Test
    fun testScenarioC_checkboxDisabled_defaultNone_filterBank_noAccountPrefill() {
        waitForMenu()

        // Configure Settings: Override OFF, Default = None
        openSettings()
        setOverrideCheckbox(false)
        setDefaultExpenseAccount(null)
        goBack()

        // Set filter to "Bank"
        setAccountFilter("Bank")

        // Open Add Expense and check prefill
        openAddExpense()
        val accountText = getAccountFieldText()
        assert(accountText.isBlank()) {
            "Expected blank account prefill but got '$accountText'"
        }

        goBack()
    }
}
