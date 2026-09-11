package app.dswriter.domain.generation

import app.dswriter.data.remote.ApiUsage
import kotlinx.coroutines.flow.Flow

enum class GenerationTaskStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }

data class GenerationTaskSubmission(
    val conversationId: String,
    val userMessageId: String,
    val assistantMessageId: String,
    val snapshot: GenerationRequestSnapshot,
)

data class GenerationTaskSummary(
    val id: String,
    val conversationId: String,
    val conversationTitle: String,
    val assistantMessageId: String,
    val modelId: String,
    val status: GenerationTaskStatus,
    val createdAt: Long,
    val errorCode: String?,
    val isUnread: Boolean,
)

interface GenerationTaskRepository {
    val recentTasks: Flow<List<GenerationTaskSummary>>
    val unreadConversationIds: Flow<Set<String>>

    suspend fun createQueued(submission: GenerationTaskSubmission): String
    suspend fun markRunning(taskId: String)
    suspend fun markCompleted(taskId: String, usage: ApiUsage?, isUnread: Boolean)
    suspend fun markFailed(taskId: String, errorCode: String)
    suspend fun markCancelled(taskId: String)
    suspend fun markConversationRead(conversationId: String)
    suspend fun recoverAfterProcessStop()
}
