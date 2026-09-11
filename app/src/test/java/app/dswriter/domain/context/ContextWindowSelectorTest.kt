package app.dswriter.domain.context

import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowSelectorTest {
    @Test
    fun `keeps current user and newest complete exchange while dropping oldest exchange`() {
        val messages = listOf(
            message(1, ConversationRole.USER, "旧问题"),
            message(2, ConversationRole.ASSISTANT, "旧回答"),
            message(3, ConversationRole.USER, "新问题"),
            message(4, ConversationRole.ASSISTANT, "新回答"),
            message(5, ConversationRole.USER, "继续"),
        )

        val result = ContextWindowSelector.select(
            messages,
            ContextSelectionBudget(8, 0, protocolTokensPerMessage = 0, requestOverheadTokens = 0),
        )

        assertEquals(listOf(3L, 4L, 5L), result.messages.map(ConversationMessage::sequence))
        assertEquals(listOf("新问题", "新回答", "继续"), result.messages.map(ConversationMessage::content))
        assertEquals(2, result.droppedMessageCount)
        assertEquals(8, result.estimatedInputTokens)
    }

    @Test
    fun `retains oversized current message without truncating unicode or emoji`() {
        val exact = "继续写这一段🙂\n不要改字"
        val result = ContextWindowSelector.select(
            listOf(message(1, ConversationRole.USER, "旧内容"), message(2, ConversationRole.USER, exact)),
            ContextSelectionBudget(4, 0, protocolTokensPerMessage = 0, requestOverheadTokens = 0),
        )

        assertEquals(listOf(exact), result.messages.map(ConversationMessage::content))
        assertEquals(1, result.droppedMessageCount)
        assertTrue(result.estimatedInputTokens > result.messages.size)
    }

    @Test
    fun `drops assistant orphan at bounded page boundary`() {
        val messages = listOf(
            message(201, ConversationRole.ASSISTANT, "上一页用户消息的回答"),
            message(202, ConversationRole.USER, "当前消息"),
        )
        val result = ContextWindowSelector.select(
            messages,
            ContextSelectionBudget(100, 0),
            totalBranchMessageCount = 202,
        )

        assertEquals(listOf("当前消息"), result.messages.map(ConversationMessage::content))
        assertEquals(201, result.droppedMessageCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects request that does not end with a real user message`() {
        ContextWindowSelector.select(
            listOf(message(1, ConversationRole.ASSISTANT, "回答")),
            ContextSelectionBudget(100, 0),
        )
    }

    @Test
    fun `reports how full a conversation is relative to the input budget`() {
        val messages = listOf(
            message(1, ConversationRole.USER, "内容".repeat(10)),
            message(2, ConversationRole.ASSISTANT, "内容".repeat(10)),
        )
        // 60 bytes per message at 3 bytes per token, plus 4 protocol tokens each, plus 2 overhead.
        val budget = ContextSelectionBudget(100, 0)
        val expectedTokens = 2 + (20 + 4) + (20 + 4)

        assertEquals(expectedTokens, ContextWindowSelector.estimateBranchTokens(messages, budget))
        assertEquals(
            expectedTokens / 100f,
            ContextWindowSelector.usageFraction(messages, budget),
            0.0001f,
        )
    }

    @Test
    fun `usage is clamped and an empty conversation reports zero`() {
        val budget = ContextSelectionBudget(10, 0)
        assertEquals(0f, ContextWindowSelector.usageFraction(emptyList(), budget), 0.0001f)

        val huge = listOf(message(1, ConversationRole.USER, "内容".repeat(500)))
        assertEquals(1f, ContextWindowSelector.usageFraction(huge, budget), 0.0001f)
    }

    private fun message(sequence: Long, role: ConversationRole, content: String) = ConversationMessage(
        id = sequence.toString(),
        conversationId = "conversation",
        parentMessageId = (sequence - 1).takeIf { it > 0 }?.toString(),
        role = role,
        content = content,
        sequence = sequence,
        createdAt = sequence,
    )
}
