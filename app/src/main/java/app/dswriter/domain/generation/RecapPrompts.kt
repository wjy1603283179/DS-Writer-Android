package app.dswriter.domain.generation

import app.dswriter.domain.context.Utf8TokenEstimator
import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationRole
import app.dswriter.domain.model.Provider
import app.dswriter.domain.model.ResolvedModel
import app.dswriter.domain.model.toResolvedModel

/**
 * Explicit, user-triggered recaps for manuscripts that outgrow the context window.
 *
 * The product rule is that a request contains only real conversation messages, and that trimmed
 * history is never replaced by a hidden summary. A recap therefore never happens by itself: the
 * user asks for one, reads it, can edit it, and decides whether it becomes the beginning of a
 * new conversation. The recap only ever reaches a model later as that real, user-visible message.
 *
 * To keep the request shape honest, the instruction below is folded into a real user message
 * rather than adding a `system` role. That keeps the persisted and transmitted role set at
 * `user`/`assistant`, which the storage boundary requires, and it means the instruction the user
 * must approve is visible in the same text they edit.
 */
object RecapPrompts {
    /**
     * Instruction prepended to the material being condensed.
     *
     * It is deliberately short and asks only for a factual recap, so the result stays a plain
     * summary of what the user actually wrote rather than new narrative the model invented.
     */
    val SUMMARY_INSTRUCTION: String = buildString {
        append("请把下面的小说正文压缩成一份前情提要。只保留：人物及身份、关键事件的发生顺序、")
        append("当前地点与时间、尚未了结的线索。不要续写剧情，不要添加原文没有的内容，")
        append("不要评论，不要分段标题，直接用连贯的中文段落写出，控制在 600 字以内。")
        append("\n\n")
        append("以下是需要压缩的正文：\n")
    }

    /**
     * A recap request plus how many real messages it actually condenses.
     *
     * The count is surfaced to the user, because a long manuscript may need more than one pass
     * and this number is what makes that visible.
     */
    data class RecapRequest(
        val snapshot: GenerationRequestSnapshot,
        val condensedMessageCount: Int,
        val remainingMessageCount: Int,
    )

    /**
     * Builds the request used to condense [history].
     *
     * The instruction and the real message text are concatenated into one user message, so the
     * request never gains a role that the product does not persist.
     *
     * Only the **oldest** messages are condensed, because those are the ones a long conversation
     * is about to lose. Taking the newest messages instead would summarize the part that is still
     * in context and leave the beginning unrecorded, which is the opposite of the intent. When a
     * manuscript is larger than one request can hold, the caller is expected to recap repeatedly,
     * newest-first each time, so each pass removes the text it just recorded.
     */
    fun buildRecapRequest(
        provider: Provider,
        history: List<ConversationMessage>,
    ): RecapRequest {
        require(history.isNotEmpty()) { "A recap needs at least one real message" }
        val selected = oldestThatFit(history, provider.toResolvedModel())
        val transcript = selected.joinToString(separator = "\n\n") { message ->
            val speaker = if (message.role == ConversationRole.USER) "用户" else "助手"
            "$speaker：${message.content}"
        }
        val condensed = ConversationMessage(
            id = selected.first().id,
            conversationId = RECAP_CONVERSATION_ID,
            parentMessageId = null,
            role = ConversationRole.USER,
            content = SUMMARY_INSTRUCTION + transcript,
            sequence = 1,
            createdAt = 0,
        )
        val snapshot = GenerationRequestSnapshotFactory.create(
            provider = provider,
            selectedMessages = listOf(condensed),
            totalBranchMessageCount = 1,
        )
        return RecapRequest(
            snapshot = snapshot,
            condensedMessageCount = selected.size,
            remainingMessageCount = history.size - selected.size,
        )
    }

    /**
     * Grows an oldest-first run of whole messages that still fits the model's input budget.
     *
     * At least one message is always kept; a single message larger than the budget is trimmed by
     * the context limiter downstream, and the user sees the result before deciding to use it.
     */
    internal fun oldestThatFit(
        history: List<ConversationMessage>,
        model: ResolvedModel,
    ): List<ConversationMessage> {
        val budget = model.contextBudget
        var tokens = SUMMARY_INSTRUCTION.toByteArray(Charsets.UTF_8).size / 3 +
            budget.requestOverheadTokens + budget.protocolTokensPerMessage
        val selected = mutableListOf<ConversationMessage>()
        for (message in history) {
            val cost = Utf8TokenEstimator.estimateMessage(message, budget.protocolTokensPerMessage)
            if (selected.isNotEmpty() && tokens + cost > budget.inputTokenLimit) break
            selected += message
            tokens += cost
        }
        return selected.ifEmpty { listOf(history.first()) }
    }

    /** Placeholder conversation id for the transient recap message; it is never persisted. */
    const val RECAP_CONVERSATION_ID = "recap"
}
