package app.dswriter.domain.context

import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationRole
import kotlin.math.ceil

data class ContextSelectionBudget(
    val maxRequestTokens: Int,
    val reservedOutputTokens: Int,
    val protocolTokensPerMessage: Int = 4,
    val requestOverheadTokens: Int = 2,
) {
    init {
        require(maxRequestTokens > 0)
        require(reservedOutputTokens in 0 until maxRequestTokens)
        require(protocolTokensPerMessage >= 0)
        require(requestOverheadTokens >= 0)
    }

    val inputTokenLimit: Int = maxRequestTokens - reservedOutputTokens
}

data class ContextSelectionResult(
    val messages: List<ConversationMessage>,
    val estimatedInputTokens: Int,
    val droppedMessageCount: Int,
)

object Utf8TokenEstimator {
    fun estimateText(text: String): Int = maxOf(1, ceil(text.toByteArray(Charsets.UTF_8).size / 3.0).toInt())

    fun estimateMessage(message: ConversationMessage, protocolTokens: Int): Int =
        estimateText(message.content) + protocolTokens + message.attachments.sumOf { attachment ->
            val tilesWide = (attachment.width + 511) / 512
            val tilesHigh = (attachment.height + 511) / 512
            maxOf(256 * tilesWide * tilesHigh, ((attachment.sizeBytes + 511) / 512).toInt())
        }
}

object ContextWindowSelector {
    /**
     * Estimated input tokens for the whole branch, without trimming.
     *
     * Used to tell the user their conversation is approaching the window before the app starts
     * dropping older messages, so a decision about recording them can be made deliberately.
     */
    fun estimateBranchTokens(
        branchMessages: List<ConversationMessage>,
        budget: ContextSelectionBudget,
    ): Int = if (branchMessages.isEmpty()) {
        0
    } else {
        budget.requestOverheadTokens + branchMessages.sumOf {
            Utf8TokenEstimator.estimateMessage(it, budget.protocolTokensPerMessage)
        }
    }

    /** Fraction of the input budget currently used, clamped to `0f..1f`. */
    fun usageFraction(
        branchMessages: List<ConversationMessage>,
        budget: ContextSelectionBudget,
    ): Float {
        val limit = budget.inputTokenLimit
        if (limit <= 0) return 1f
        return (estimateBranchTokens(branchMessages, budget).toFloat() / limit).coerceIn(0f, 1f)
    }

    fun select(
        branchMessages: List<ConversationMessage>,
        budget: ContextSelectionBudget,
        totalBranchMessageCount: Int = branchMessages.size,
    ): ContextSelectionResult {
        require(branchMessages.isNotEmpty()) { "At least one real message is required" }
        require(totalBranchMessageCount >= branchMessages.size)
        val candidates = if (
            totalBranchMessageCount > branchMessages.size &&
            branchMessages.first().role == ConversationRole.ASSISTANT
        ) {
            branchMessages.drop(1)
        } else {
            branchMessages
        }
        require(candidates.last().role == ConversationRole.USER) {
            "The current message must be a real user message"
        }

        val selectedReversed = mutableListOf(candidates.last())
        var estimated = budget.requestOverheadTokens +
            Utf8TokenEstimator.estimateMessage(candidates.last(), budget.protocolTokensPerMessage)
        var index = candidates.lastIndex - 1

        while (index >= 0) {
            val unitStart = if (
                candidates[index].role == ConversationRole.ASSISTANT &&
                index > 0 &&
                candidates[index - 1].role == ConversationRole.USER
            ) {
                index - 1
            } else {
                index
            }
            val unit = candidates.subList(unitStart, index + 1)
            val unitTokens = unit.sumOf {
                Utf8TokenEstimator.estimateMessage(it, budget.protocolTokensPerMessage)
            }
            if (estimated + unitTokens > budget.inputTokenLimit) break
            unit.asReversed().forEach(selectedReversed::add)
            estimated += unitTokens
            index = unitStart - 1
        }

        val selected = selectedReversed.asReversed()
        return ContextSelectionResult(
            messages = selected,
            estimatedInputTokens = estimated,
            droppedMessageCount = totalBranchMessageCount - selected.size,
        )
    }
}
