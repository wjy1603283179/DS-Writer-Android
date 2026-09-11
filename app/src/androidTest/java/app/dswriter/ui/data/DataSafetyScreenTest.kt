package app.dswriter.ui.data

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dswriter.domain.model.TrashedConversation
import app.dswriter.ui.theme.DSWriterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DataSafetyScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun showsReadableExportsAndConversationTrashWithoutJsonBackup() {
        var deleted = ""
        composeRule.setContent {
            DSWriterTheme {
                DataSafetyScreen(
                    state = DataSafetyUiState(
                        trashedConversations = listOf(TrashedConversation("conversation", "旧对话", 2)),
                    ),
                    onBack = {}, onExportMarkdown = {}, onExportText = {}, onRestoreConversation = {},
                    onDeleteConversation = { deleted = it }, onDismissNotice = {},
                )
            }
        }
        composeRule.onNodeWithText("数据与回收站").assertIsDisplayed()
        composeRule.onNodeWithText("导出 Markdown").assertIsDisplayed()
        composeRule.onNodeWithText("旧对话").assertIsDisplayed()
        composeRule.onAllNodesWithText("永久删除")[0].performClick()
        composeRule.onNodeWithText("确认永久删除").assertIsDisplayed()
        composeRule.onAllNodesWithText("永久删除")[1].performClick()
        assertEquals("conversation", deleted)
    }
}
