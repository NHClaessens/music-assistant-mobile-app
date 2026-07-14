package io.music_assistant.client.ui.compose.search

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SearchInputTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `requests focus`() {
        composeTestRule.setContent {
            SearchInput(onSearch = {}, placeHolder = "Search query")
        }

        composeTestRule.onNodeWithText("Search query").assertIsFocused()
    }

    @Test
    fun `does not request focus when query is not empty after restore`() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            SearchInput(onSearch = {}, placeHolder = "Search query")
        }

        composeTestRule.onNodeWithText("Search query").performTextReplacement("blah")

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithText("blah").assertIsNotFocused()
    }
}
