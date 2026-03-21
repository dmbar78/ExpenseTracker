package com.example.expensetracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.ui.components.CategoryPieChart
import com.example.expensetracker.ui.components.KeywordBreakdownHeader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeywordBreakdownUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun keywordSelectionMovesBetweenRowsIncludingNoKeyword() {
        composeTestRule.setContent {
            var selectedLabel by mutableStateOf<String?>(null)
            MaterialTheme {
                CategoryPieChart(
                    entries = listOf("(No Keyword)" to 84.12, "car" to 15.88),
                    onSectorTapped = { selectedLabel = it },
                    selectedLabel = selectedLabel
                )
            }
        }

        composeTestRule.onNodeWithTag("${TestTags.HOME_DIAGRAM_SECTOR_PREFIX}car")
            .assertIsNotSelected()
        composeTestRule.onNodeWithTag("${TestTags.HOME_DIAGRAM_SECTOR_PREFIX}(No Keyword)")
            .assertIsNotSelected()

        composeTestRule.onNodeWithTag("${TestTags.HOME_DIAGRAM_SECTOR_PREFIX}car")
            .performClick()

        composeTestRule.onNodeWithTag("${TestTags.HOME_DIAGRAM_SECTOR_PREFIX}car")
            .assertIsSelected()
        composeTestRule.onNodeWithTag("${TestTags.HOME_DIAGRAM_SECTOR_PREFIX}(No Keyword)")
            .assertIsNotSelected()

        composeTestRule.onNodeWithTag("${TestTags.HOME_DIAGRAM_SECTOR_PREFIX}(No Keyword)")
            .performClick()

        composeTestRule.onNodeWithTag("${TestTags.HOME_DIAGRAM_SECTOR_PREFIX}(No Keyword)")
            .assertIsSelected()
        composeTestRule.onNodeWithTag("${TestTags.HOME_DIAGRAM_SECTOR_PREFIX}car")
            .assertIsNotSelected()
    }

    @Test
    fun keywordResetIconVisibleOnlyWhenKeywordSelected() {
        var selectedLabel by mutableStateOf<String?>(null)

        composeTestRule.setContent {
            MaterialTheme {
                KeywordBreakdownHeader(
                    selectedKeywordLabel = selectedLabel,
                    onResetSelection = {},
                    resetButtonTestTag = "keyword_reset_test"
                )
            }
        }

        composeTestRule.onNodeWithTag("keyword_reset_test").assertDoesNotExist()

        composeTestRule.runOnUiThread {
            selectedLabel = "car"
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("keyword_reset_test").assertExists()
    }

    @Test
    fun keywordResetIconClickClearsSelection() {
        composeTestRule.setContent {
            var selectedLabel by mutableStateOf<String?>("car")
            MaterialTheme {
                KeywordBreakdownHeader(
                    selectedKeywordLabel = selectedLabel,
                    onResetSelection = { selectedLabel = null },
                    resetButtonTestTag = "keyword_reset_test"
                )
            }
        }

        composeTestRule.onNodeWithTag("keyword_reset_test").assertExists().performClick()
        composeTestRule.onNodeWithTag("keyword_reset_test").assertDoesNotExist()
    }
}
