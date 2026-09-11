package app.dswriter.ui.tasks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.dswriter.domain.generation.GenerationTaskStatus
import app.dswriter.domain.generation.GenerationTaskSummary
import app.dswriter.ui.theme.DSWriterTheme
import org.junit.Rule
import org.junit.Test

class TasksScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun showsQueuedTaskCancellationAndUnreadIndicatorInChinese() {
        composeRule.setContent {
            DSWriterTheme {
                TasksScreen(
                    tasks = listOf(
                        GenerationTaskSummary(
                            id = "task",
                            conversationId = "conversation",
                            conversationTitle = "章节讨论",
                            assistantMessageId = "assistant",
                            modelId = "deepseek-v4-flash",
                            status = GenerationTaskStatus.QUEUED,
                            createdAt = 1,
                            errorCode = null,
                            isUnread = false,
                        ),
                        GenerationTaskSummary(
                            id = "completed",
                            conversationId = "completed-conversation",
                            conversationTitle = "已完成对话",
                            assistantMessageId = "completed-assistant",
                            modelId = "deepseek-v4-pro",
                            status = GenerationTaskStatus.COMPLETED,
                            createdAt = 2,
                            errorCode = null,
                            isUnread = true,
                        ),
                    ),
                    onBack = {},
                    onOpenConversation = {},
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithText("正在排队").assertIsDisplayed()
        composeRule.onNodeWithText("取消任务").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("未读生成结果").assertIsDisplayed()
    }
}
