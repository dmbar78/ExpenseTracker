package com.example.expensetracker.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.MainActivity
import com.example.expensetracker.R
import com.example.expensetracker.data.FilterPreferences
import com.example.expensetracker.data.SecurityManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsCsvExportDialogTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    // Avoid system permission interruption from voice-recognition features.
    @get:Rule(order = 1)
    val permissionRule: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 2)
    val composeTestRule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<
        androidx.test.ext.junit.rules.ActivityScenarioRule<MainActivity>,
        MainActivity
    > = createAndroidComposeRule<MainActivity>()

    private val ctx: Context by lazy {
        androidx.test.core.app.ApplicationProvider.getApplicationContext()
    }

    @Before
    fun init() {
        hiltRule.inject()
        ctx.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking {
            FilterPreferences(ctx).clearAllFilters()
        }
        SecurityManager.setLocked()
    }

    @Test
    fun exportReportingCsvOpensDialogWithAllPeriodOptions() {
        openSettings()
        openCsvDialog()

        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_DIALOG).assertExists()
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_DAY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_WEEK).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_MONTH).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_YEAR).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_CUSTOM).assertIsDisplayed()
    }

    @Test
    fun exportReportingCsvOkWithoutSelectionShowsValidationErrorAndKeepsDialogOpen() {
        openSettings()
        openCsvDialog()

        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_OK).performClick()

        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_DIALOG).assertExists()
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_ERROR)
            .assertExists()
            .assertTextContains(ctx.getString(R.string.msg_choose_reporting_period))
    }

    @Test
    fun exportReportingCsvCancelClosesDialogWithoutStartingExport() {
        openSettings()
        openCsvDialog()

        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_CANCEL).performClick()

        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_DIALOG).assertDoesNotExist()
    }

    @Test
    fun exportReportingCsvPrefillsFromActiveWeekFilter() {
        setWeekFilterFromHome()
        openSettings()
        openCsvDialog()

        composeTestRule.onNodeWithTag(TestTags.SETTINGS_CSV_PERIOD_WEEK).assertIsSelected()
    }

    private fun waitForMenu() {
        val menuDesc = ctx.getString(R.string.menu_desc)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeTestRule.onNodeWithContentDescription(menuDesc).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }

    private fun openSettings() {
        waitForMenu()
        val menuDesc = ctx.getString(R.string.menu_desc)
        composeTestRule.onNodeWithContentDescription(menuDesc).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(ctx.getString(R.string.nav_settings)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun openCsvDialog() {
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_EXPORT_REPORTING_CSV)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun setWeekFilterFromHome() {
        waitForMenu()
        composeTestRule.onNodeWithContentDescription("Filter").performClick()
        composeTestRule.onNodeWithText(ctx.getString(R.string.lbl_filter_time)).performClick()
        composeTestRule.onNodeWithText(ctx.getString(R.string.lbl_time_week)).performClick()
        composeTestRule.onNodeWithText(ctx.getString(R.string.btn_ok)).performClick()
        composeTestRule.waitForIdle()
    }
}
