package app.dswriter.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dswriter.domain.generation.GenerationRequestSnapshot
import app.dswriter.domain.generation.RequestMessageSnapshot
import app.dswriter.domain.generation.RequestRole
import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationMessageStatus
import app.dswriter.domain.model.ConversationRole
import app.dswriter.domain.model.ConversationSummary
import app.dswriter.domain.model.PreparedImageAttachment
import java.util.concurrent.atomic.AtomicBoolean
import app.dswriter.ui.theme.DSWriterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class ChatScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun showsChineseConversationControlsAndAccessibleStopAction() {
        composeRule.setContent {
            DSWriterTheme {
                ChatScreen(
                    state = ChatUiState(
                        conversation = ConversationSummary(
                            "conversation",
                            "人物讨论",
                            "deepseek-v4-flash",
                            "",
                            1,
                        ),
                        messages = listOf(
                            ConversationMessage(
                                id = "message",
                                conversationId = "conversation",
                                parentMessageId = null,
                                role = ConversationRole.ASSISTANT,
                                content = "正在回复",
                                generationStatus = ConversationMessageStatus.STREAMING,
                                sequence = 1,
                                createdAt = 1,
                            ),
                        ),
                        isGenerating = true,
                    ),
                    onDraftChanged = {},
                    onSend = {},
                    onStop = {},
                    onLoadOlder = {},
                    onRegenerate = {},
                    onProviderSelected = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithText("人物讨论").assertIsDisplayed()
        composeRule.onNodeWithText("输入消息").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("停止生成").assertIsDisplayed()
    }

    @Test
    fun rendersLongSelectableAssistantText() {
        val longText = "长文本段落。".repeat(5_000)
        composeRule.setContent {
            DSWriterTheme {
                ChatScreen(
                    state = ChatUiState(
                        conversation = ConversationSummary(
                            "conversation", "长篇对话", "deepseek-v4-pro", "", 1,
                        ),
                        messages = listOf(
                            ConversationMessage(
                                id = "long",
                                conversationId = "conversation",
                                parentMessageId = null,
                                role = ConversationRole.ASSISTANT,
                                content = longText,
                                sequence = 1,
                                createdAt = 1,
                            ),
                        ),
                    ),
                    onDraftChanged = {},
                    onSend = {},
                    onStop = {},
                    onLoadOlder = {},
                    onRegenerate = {},
                    onProviderSelected = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithText("长文本段落。长文本段落。", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("选择图片").assertIsDisplayed()
    }

    @Test
    fun debugInspectorShowsExactMessagesAndNeverContainsCredential() {
        composeRule.setContent {
            DSWriterTheme {
                ChatScreen(
                    state = ChatUiState(
                        conversation = ConversationSummary(
                            "conversation", "检查请求", "deepseek-v4-flash", "", 1,
                        ),
                        requestSnapshot = GenerationRequestSnapshot(
                            baseUrl = "https://api.deepseek.com",
                            modelId = "deepseek-v4-flash",
                            messages = listOf(
                                RequestMessageSnapshot(RequestRole.USER, " 继续🙂\n"),
                            ),
                            estimatedInputTokens = 9,
                            droppedMessageCount = 3,
                        ),
                    ),
                    onDraftChanged = {},
                    onSend = {},
                    onStop = {},
                    onLoadOlder = {},
                    onRegenerate = {},
                    onProviderSelected = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithText("检查实际请求").performClick()
        composeRule.onNodeWithText(" 继续🙂\n").assertIsDisplayed()
        composeRule.onNodeWithText("API 密钥：未包含").assertIsDisplayed()
        composeRule.onNodeWithText("secret", substring = true).assertDoesNotExist()
    }

    @Test
    fun imageRequiresExplicitOneTapVisionSwitch() {
        val switched = AtomicBoolean(false)
        composeRule.setContent {
            DSWriterTheme {
                ChatScreen(
                    state = ChatUiState(
                        conversation = ConversationSummary(
                            "conversation", "图片对话", "deepseek-v4-flash", "", 1,
                        ),
                        pendingImage = PendingImageState.Ready(
                            PreparedImageAttachment(
                                "file:/missing-preview.jpg", "image/jpeg", "手稿.jpg", 800, 600, 100,
                            ),
                        ),
                        needsVisionModel = true,
                    ),
                    onDraftChanged = {},
                    onSend = {},
                    onStop = {},
                    onLoadOlder = {},
                    onRegenerate = {},
                    onProviderSelected = {},
                    onDismissError = {},
                    onSwitchToVision = { switched.set(true) },
                )
            }
        }

        composeRule.onNodeWithText("当前模型不支持图片").assertIsDisplayed()
        composeRule.onNodeWithText("选择模型").performClick()
        assertTrue(switched.get())
    }

    @Test
    fun longReasoningIsCollapsedByDefaultAndUsesPagedExpansion() {
        val reasoning = "开头标记" + "思考内容。".repeat(2_500) + "末尾标记"
        composeRule.setContent {
            DSWriterTheme {
                ChatScreen(
                    state = ChatUiState(
                        conversation = ConversationSummary(
                            "conversation", "长思考", "deepseek-v4-pro", "", 1,
                        ),
                        messages = listOf(
                            ConversationMessage(
                                id = "reasoning",
                                conversationId = "conversation",
                                parentMessageId = null,
                                role = ConversationRole.ASSISTANT,
                                content = "最终回答",
                                reasoningContent = reasoning,
                                sequence = 1,
                                createdAt = 1,
                            ),
                        ),
                    ),
                    onDraftChanged = {}, onSend = {}, onStop = {}, onLoadOlder = {},
                    onRegenerate = {}, onProviderSelected = {}, onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithText("思考过程").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("第 1 / 3 段").assertIsDisplayed()
        composeRule.onNodeWithText("开头标记", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("下一段").performClick()
        composeRule.onNodeWithText("第 2 / 3 段").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("隐藏思考过程").performClick()
        composeRule.onNodeWithContentDescription("显示思考过程").assertIsDisplayed()
    }
}
