package app.dswriter.data.repository

import androidx.room.withTransaction
import app.dswriter.data.local.db.DSWriterDatabase
import app.dswriter.data.local.db.GenerationTaskDao
import app.dswriter.data.local.db.GenerationTaskEntity
import app.dswriter.data.local.db.GenerationTaskState
import app.dswriter.data.local.db.GenerationTaskWithTitles
import app.dswriter.data.local.db.UsageDao
import app.dswriter.data.local.db.UsageEntity
import app.dswriter.data.remote.ApiUsage
import app.dswriter.domain.generation.GenerationTaskRepository
import app.dswriter.domain.generation.GenerationTaskStatus
import app.dswriter.domain.generation.GenerationTaskSubmission
import app.dswriter.domain.generation.GenerationTaskSummary
import app.dswriter.domain.model.AppClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class RoomGenerationTaskRepository @Inject constructor(
    private val database: DSWriterDatabase,
    private val taskDao: GenerationTaskDao,
    private val usageDao: UsageDao,
    private val clock: AppClock,
    private val json: Json,
) : GenerationTaskRepository {
    override val recentTasks: Flow<List<GenerationTaskSummary>> =
        taskDao.observeRecent().map { rows -> rows.map { it.toModel() } }

    override val unreadConversationIds: Flow<Set<String>> =
        taskDao.observeUnreadConversationIds().map(List<String>::toSet)

    override suspend fun createQueued(submission: GenerationTaskSubmission): String {
        val id = UUID.randomUUID().toString()
        val now = clock.currentTimeMillis()
        database.withTransaction {
            taskDao.insert(
                GenerationTaskEntity(
                    id = id,
                    conversationId = submission.conversationId,
                    userMessageId = submission.userMessageId,
                    assistantMessageId = submission.assistantMessageId,
                    requestSnapshotJson = json.encodeToString(submission.snapshot),
                    modelId = submission.snapshot.modelId,
                    baseUrl = submission.snapshot.baseUrl,
                    state = GenerationTaskState.QUEUED,
                    queueOrder = taskDao.maxQueueOrder() + 1,
                    createdAt = now,
                ),
            )
        }
        return id
    }

    override suspend fun markRunning(taskId: String) {
        check(taskDao.markRunning(taskId, clock.currentTimeMillis()) == 1)
    }

    override suspend fun markCompleted(taskId: String, usage: ApiUsage?, isUnread: Boolean) {
        database.withTransaction {
            check(taskDao.markCompleted(taskId, clock.currentTimeMillis(), isUnread) == 1)
            if (usage != null) {
                usageDao.insert(
                    UsageEntity(
                        id = UUID.randomUUID().toString(),
                        generationTaskId = taskId,
                        assistantMessageId = requireNotNull(taskDao.assistantMessageId(taskId)),
                        promptTokens = usage.promptTokens,
                        completionTokens = usage.completionTokens,
                        totalTokens = usage.totalTokens,
                        promptCacheHitTokens = usage.promptCacheHitTokens,
                        promptCacheMissTokens = usage.promptCacheMissTokens,
                    ),
                )
            }
        }
    }

    override suspend fun markFailed(taskId: String, errorCode: String) {
        check(taskDao.markFailed(taskId, clock.currentTimeMillis(), errorCode) == 1)
    }

    override suspend fun markCancelled(taskId: String) {
        check(taskDao.markCancelled(taskId, clock.currentTimeMillis()) == 1)
    }

    override suspend fun markConversationRead(conversationId: String) {
        taskDao.markConversationRead(conversationId)
    }

    override suspend fun recoverAfterProcessStop() {
        database.withTransaction {
            database.messageDao().markRunningTaskOutputsInterrupted()
            database.messageDao().markQueuedTaskOutputsCancelled()
            taskDao.interruptRunning(clock.currentTimeMillis())
            taskDao.cancelQueuedAfterProcessStop(clock.currentTimeMillis())
        }
    }

    private fun GenerationTaskWithTitles.toModel() = GenerationTaskSummary(
        id = task.id,
        conversationId = task.conversationId,
        conversationTitle = conversationTitle,
        assistantMessageId = requireNotNull(task.assistantMessageId),
        modelId = task.modelId,
        status = GenerationTaskStatus.valueOf(task.state.name),
        createdAt = task.createdAt,
        errorCode = task.errorCode,
        isUnread = task.isUnread,
    )
}
