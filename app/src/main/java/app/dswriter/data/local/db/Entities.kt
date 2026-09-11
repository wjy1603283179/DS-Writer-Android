package app.dswriter.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val nextConversationNumber: Long = 1,
    val migrationNoticePending: Boolean = false,
) {
    companion object { const val SINGLETON_ID = 1 }
}

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["deletedAt", "updatedAt"]),
        Index(value = ["branchHeadMessageId"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val selectedModelId: String,
    val branchHeadMessageId: String? = null,
    val draft: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversationId", "sequence"], unique = true),
        Index(value = ["parentMessageId"]),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val parentMessageId: String? = null,
    val role: MessageRole,
    val content: String,
    val reasoningContent: String = "",
    val sequence: Long,
    val createdAt: Long,
    val generationStatus: MessageGenerationStatus = MessageGenerationStatus.COMPLETE,
)

enum class MessageRole { USER, ASSISTANT }

enum class MessageGenerationStatus { STREAMING, COMPLETE, FAILED, CANCELLED, INTERRUPTED }

@Entity(
    tableName = "generation_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversationId", "createdAt"]),
        Index(value = ["state", "queueOrder"]),
        Index(value = ["conversationId", "isUnread"]),
    ],
)
data class GenerationTaskEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val userMessageId: String,
    val assistantMessageId: String? = null,
    val requestSnapshotJson: String,
    val modelId: String,
    val baseUrl: String,
    val state: GenerationTaskState,
    val queueOrder: Long,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorCode: String? = null,
    val isUnread: Boolean = false,
)

enum class GenerationTaskState { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["messageId"])],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val localUri: String,
    val mimeType: String,
    val displayName: String,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long,
    val preparationState: AttachmentPreparationState,
)

enum class AttachmentPreparationState { SELECTED, PREPARING, READY, FAILED }

@Entity(
    tableName = "usage",
    foreignKeys = [
        ForeignKey(
            entity = GenerationTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["generationTaskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["generationTaskId"], unique = true)],
)
data class UsageEntity(
    @PrimaryKey val id: String,
    val generationTaskId: String,
    val assistantMessageId: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
    val promptCacheHitTokens: Long? = null,
    val promptCacheMissTokens: Long? = null,
)
