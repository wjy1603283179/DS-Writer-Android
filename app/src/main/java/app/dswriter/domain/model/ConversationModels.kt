package app.dswriter.domain.model

data class TrashedConversation(val id: String, val title: String, val deletedAt: Long)

data class ConversationSummary(
    val id: String,
    val title: String,
    val selectedModelId: String,
    val draft: String,
    val updatedAt: Long,
)

data class ConversationMessage(
    val id: String,
    val conversationId: String,
    val parentMessageId: String?,
    val role: ConversationRole,
    val content: String,
    val reasoningContent: String = "",
    val generationStatus: ConversationMessageStatus = ConversationMessageStatus.COMPLETE,
    val sequence: Long,
    val createdAt: Long,
    val attachments: List<ConversationAttachment> = emptyList(),
    val reasoningCharacterCount: Int = reasoningContent.length,
    val reasoningIsPreview: Boolean = false,
)

data class ConversationAttachment(
    val id: String,
    val messageId: String,
    val localUri: String,
    val mimeType: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val preparationState: AttachmentState,
)

data class PreparedImageAttachment(
    val localUri: String,
    val mimeType: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

enum class AttachmentState { SELECTED, PREPARING, READY, FAILED }
enum class ConversationRole { USER, ASSISTANT }
enum class ConversationMessageStatus { STREAMING, COMPLETE, FAILED, CANCELLED, INTERRUPTED }
