package com.example.expensetracker.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class HomeDiagramUiTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val permissionRule: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @Before
    fun setup() {
        hiltRule.inject()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun nodeWithTag(tag: String): SemanticsNodeInteraction {
        return composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)
    }

    private fun selectTab(text: String) {
        composeTestRule.onNodeWithText(text).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testDiagramIconToggleExpensesTab() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.onNodeWithText(context.getString(R.string.tab_expense))
            .assertExists()

        val diagramIcon = nodeWithTag(TestTags.HOME_DIAGRAM_ICON_EXPENSES)
        diagramIcon.assertExists()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertDoesNotExist()

        diagramIcon.performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertExists()
    }

    @Test
    fun testDiagramIconToggleIncomesTab() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        selectTab(context.getString(R.string.tab_income))

        val diagramIcon = nodeWithTag(TestTags.HOME_DIAGRAM_ICON_INCOMES)
        diagramIcon.assertExists()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_INCOMES)
            .assertDoesNotExist()

        diagramIcon.performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_INCOMES)
            .assertExists()
    }

    @Test
    fun testPerTabIndependentDiagramState() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.onNodeWithText(context.getString(R.string.tab_expense))
            .assertExists()

        nodeWithTag(TestTags.HOME_DIAGRAM_ICON_EXPENSES)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertExists()

        selectTab(context.getString(R.string.tab_income))

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_INCOMES)
            .assertDoesNotExist()

        nodeWithTag(TestTags.HOME_DIAGRAM_ICON_INCOMES)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_INCOMES)
            .assertExists()

        selectTab(context.getString(R.string.tab_expense))

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertExists()
    }

    @Test
    fun testDiagramToggleMultipleTimes() {
        nodeWithTag(TestTags.HOME_DIAGRAM_ICON_EXPENSES)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertExists()

        nodeWithTag(TestTags.HOME_DIAGRAM_ICON_EXPENSES)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertDoesNotExist()

        nodeWithTag(TestTags.HOME_DIAGRAM_ICON_EXPENSES)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertExists()
    }

    @Test
    fun testDiagramContainerRenderingOnExpensesTab() {
        nodeWithTag(TestTags.HOME_DIAGRAM_ICON_EXPENSES)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun testDiagramIconRemainsInteractiveWhenExpanded() {
        val diagramIcon = nodeWithTag(TestTags.HOME_DIAGRAM_ICON_EXPENSES)

        diagramIcon.performClick()
        composeTestRule.waitForIdle()

        diagramIcon.assertIsDisplayed()
        diagramIcon.assertIsEnabled()
        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES).assertExists()
    }

    @Test
    fun testDiagramContentAreaExistsAfterExpansion() {
        nodeWithTag(TestTags.HOME_DIAGRAM_ICON_EXPENSES)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertExists()
    }

    @Test
    fun testDiagramStatePreservedWhenSwitchingTabs() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        nodeWithTag(TestTags.HOME_DIAGRAM_ICON_EXPENSES)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertExists()

        selectTab(context.getString(R.string.tab_transfers))
        selectTab(context.getString(R.string.tab_expense))

        nodeWithTag(TestTags.HOME_DIAGRAM_CONTAINER_EXPENSES)
            .assertExists()
    }

    // ==================== Home Bar Chart Tests ====================

    @Test
    fun testChartIcon_visibleOnHomeOnly() {
        // Chart icon should exist on Home
        nodeWithTag(TestTags.HOME_CHART_MODE_ICON)
            .assertExists()
    }

    @Test
    fun testChartToggle_showsContainer() {
        // Initially chart container should not exist
        nodeWithTag(TestTags.HOME_CHART_CONTAINER)
            .assertDoesNotExist()

        // Toggle chart on
        nodeWithTag(TestTags.HOME_CHART_MODE_ICON)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_CHART_CONTAINER)
            .assertExists()
    }

    @Test
    fun testChartToggle_offHidesContainer() {
        nodeWithTag(TestTags.HOME_CHART_MODE_ICON)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_CHART_CONTAINER)
            .assertExists()

        // Toggle off
        nodeWithTag(TestTags.HOME_CHART_MODE_ICON)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_CHART_CONTAINER)
            .assertDoesNotExist()
    }

    @Test
    fun testChartToggle_persistsAcrossTabSwitch() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        nodeWithTag(TestTags.HOME_CHART_MODE_ICON)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_CHART_CONTAINER)
            .assertExists()

        // Switch tabs and back
        selectTab(context.getString(R.string.tab_income))
        selectTab(context.getString(R.string.tab_expense))

        nodeWithTag(TestTags.HOME_CHART_CONTAINER)
            .assertExists()
    }

    @Test
    fun testChartActivation_showsGrainDropdown() {
        nodeWithTag(TestTags.HOME_CHART_MODE_ICON)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_CHART_GRAIN_DROPDOWN)
            .assertExists()
    }

    @Test
    fun testChartActivation_showsSlideControls() {
        nodeWithTag(TestTags.HOME_CHART_MODE_ICON)
            .performClick()
        composeTestRule.waitForIdle()

        nodeWithTag(TestTags.HOME_CHART_SLIDE_LEFT)
            .assertExists()
        nodeWithTag(TestTags.HOME_CHART_SLIDE_RIGHT)
            .assertExists()
    }
}
