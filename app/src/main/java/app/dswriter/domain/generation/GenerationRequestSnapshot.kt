package app.dswriter.domain.generation

import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationRole
import app.dswriter.domain.model.ModelTuning
import app.dswriter.domain.model.Provider
import app.dswriter.domain.model.toResolvedModel
import app.dswriter.domain.context.ContextWindowSelector
import kotlinx.serialization.Serializable

@Serializable
data class GenerationRequestSnapshot(
    val baseUrl: String,
    val modelId: String,
    val messages: List<RequestMessageSnapshot>,
    val estimatedInputTokens: Int = 0,
    val droppedMessageCount: Int = 0,
    /**
     * Optional sampling and length parameters. Request metadata only: it never contributes a
     * message, so it cannot affect the pure-conversation guarantee.
     */
    val tuning: ModelTuning? = null,
)

@Serializable
data class RequestMessageSnapshot(
    val role: RequestRole,
    val content: String,
    val attachments: List<RequestAttachmentSnapshot> = emptyList(),
)

@Serializable
data class RequestAttachmentSnapshot(
    val id: String,
    val localUri: String,
    val mimeType: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

class VisionModelRequiredException : IllegalArgumentException("Selected model does not support images")
class AttachmentUnavailableException : IllegalArgumentException("Selected context contains an unavailable image")

@Serializable
enum class RequestRole { USER, ASSISTANT }

object GenerationRequestSnapshotFactory {
    /**
     * Builds the request from the active provider.
     *
     * There is one path for this, and it reads only real branch messages plus the provider's
     * routing and sampling settings. No provider table is consulted, because the application has
     * none: whatever the user configured is what gets sent.
     */
    fun create(
        provider: Provider,
        selectedMessages: List<ConversationMessage>,
        totalBranchMessageCount: Int = selectedMessages.size,
    ): GenerationRequestSnapshot {
        require(provider.baseUrl.isNotBlank()) { "The provider has no base URL" }
        require(provider.modelId.isNotBlank()) { "The provider has no model id" }
        val model = provider.toResolvedModel()
        val selection = ContextWindowSelector.select(
            selectedMessages,
            model.contextBudget,
            totalBranchMessageCount,
        )
        if (selection.messages.any { it.role == ConversationRole.ASSISTANT && it.attachments.isNotEmpty() }) {
            throw IllegalArgumentException("Assistant messages cannot own request images")
        }
        if (selection.messages.any { message ->
                message.attachments.any { it.preparationState != app.dswriter.domain.model.AttachmentState.READY }
            }
        ) throw AttachmentUnavailableException()
        if (!model.supportsVision && selection.messages.any { it.attachments.isNotEmpty() }) {
            throw VisionModelRequiredException()
        }
        return GenerationRequestSnapshot(
            baseUrl = provider.baseUrl,
            modelId = provider.modelId,
            messages = selection.messages.map { message ->
                RequestMessageSnapshot(
                    role = if (message.role == ConversationRole.USER) RequestRole.USER else RequestRole.ASSISTANT,
                    content = message.content,
                    attachments = message.attachments.map { attachment ->
                        RequestAttachmentSnapshot(
                            id = attachment.id,
                            localUri = attachment.localUri,
                            mimeType = attachment.mimeType,
                            displayName = attachment.displayName,
                            width = attachment.width,
                            height = attachment.height,
                            sizeBytes = attachment.sizeBytes,
                        )
                    },
                )
            },
            estimatedInputTokens = selection.estimatedInputTokens,
            droppedMessageCount = selection.droppedMessageCount,
            tuning = model.effectiveTuning.takeIf { !it.isEmpty },
        )
    }
}
