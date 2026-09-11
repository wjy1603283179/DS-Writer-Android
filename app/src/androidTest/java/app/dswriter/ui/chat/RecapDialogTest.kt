package app.dswriter.ui.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import app.dswriter.ui.theme.DSWriterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecapDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheRecapForReviewAndSendsTheEditedText() {
        var submitted: String? = null
        composeRule.setContent {
            DSWriterTheme {
                RecapDialog(
                    recap = RecapState.Ready(
                        sourceConversationTitle = "新对话 1",
                        text = "林砚之在雨夜走进旧书店，拿到一张过期船票。",
                        condensedMessageCount = 12,
                        remainingMessageCount = 0,
                    ),
                    onDismiss = {},
                    onUseAsNewConversation = { submitted = it },
                )
            }
        }

        composeRule.onNodeWithText("前情提要").assertExists()
        composeRule.onNodeWithText("林砚之在雨夜走进旧书店，拿到一张过期船票。").assertExists()
        // The review step must say plainly that nothing enters a conversation on its own.
        composeRule.onNodeWithText(
            "下面是模型压缩出的提要，请先读一遍并按需要修改。只有你点“用它开始新对话”之后，" +
                "它才会作为你的一条真实消息进入新对话。",
        ).assertExists()

        composeRule.onNodeWithText("林砚之在雨夜走进旧书店，拿到一张过期船票。")
            .performTextReplacement("用户修改后的前情提要")
        composeRule.onNodeWithText("用它开始新对话").performClick()

        assertEquals("用户修改后的前情提要", submitted)
    }

    @Test
    fun asksForConsentBeforeTheOneRequestThatIsNotAConversationTurn() {
        var confirmed = false
        composeRule.setContent {
            DSWriterTheme {
                RecapConsentDialog(
                    isGenerating = false,
                    onDismiss = {},
                    onConfirm = { confirmed = true },
                )
            }
        }

        composeRule.onNodeWithText("生成前情提要").assertExists()
        composeRule.onNodeWithText("开始生成").performClick()

        assertEquals(true, confirmed)
    }
}
