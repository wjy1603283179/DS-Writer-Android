package app.dswriter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Embedded
import kotlinx.coroutines.flow.Flow

@Dao
interface AppStateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensure(state: AppStateEntity): Long

    @Query("SELECT * FROM app_state WHERE id = 1")
    suspend fun get(): AppStateEntity?

    @Query("UPDATE app_state SET nextConversationNumber = nextConversationNumber + 1 WHERE id = 1")
    suspend fun incrementConversationNumber(): Int

    @Query("UPDATE app_state SET migrationNoticePending = 0 WHERE id = 1 AND migrationNoticePending = 1")
    suspend fun consumeMigrationNotice(): Int
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<ConversationEntity>>

    @Query(
        "SELECT * FROM conversations WHERE deletedAt IS NULL AND instr(title, :query) > 0 " +
            "ORDER BY updatedAt DESC LIMIT :limit",
    )
    fun searchByTitle(query: String, limit: Int = 100): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun rename(id: String, title: String, updatedAt: Long): Int

    @Query("UPDATE conversations SET draft = :draft, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun updateDraft(id: String, draft: String, updatedAt: Long): Int

    @Query("UPDATE conversations SET selectedModelId = :modelId, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun updateSelectedModel(id: String, modelId: String, updatedAt: Long): Int

    @Query("UPDATE conversations SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, deletedAt: Long): Int

    @Query("UPDATE conversations SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun restore(id: String, updatedAt: Long): Int

    @Query("DELETE FROM conversations WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun deletePermanently(id: String): Int

    @Query("UPDATE conversations SET branchHeadMessageId = :messageId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBranchHead(id: String, messageId: String, updatedAt: Long)
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM messages WHERE conversationId = :conversationId")
    suspend fun maxSequence(conversationId: String): Long

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId " +
            "AND sequence < :beforeSequence ORDER BY sequence DESC LIMIT :limit",
    )
    suspend fun loadPage(
        conversationId: String,
        beforeSequence: Long = Long.MAX_VALUE,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        "WITH RECURSIVE branch AS (" +
            "SELECT m.* FROM messages m INNER JOIN conversations c ON c.branchHeadMessageId = m.id " +
            "WHERE c.id = :conversationId UNION ALL " +
            "SELECT parent.* FROM messages parent INNER JOIN branch child ON child.parentMessageId = parent.id" +
            ") SELECT * FROM (SELECT * FROM branch ORDER BY sequence DESC LIMIT :limit) ORDER BY sequence ASC",
    )
    fun observeSelectedBranch(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    @Query(
        "WITH RECURSIVE branch AS (" +
            "SELECT m.* FROM messages m INNER JOIN conversations c ON c.branchHeadMessageId = m.id " +
            "WHERE c.id = :conversationId UNION ALL " +
            "SELECT parent.* FROM messages parent INNER JOIN branch child ON child.parentMessageId = parent.id" +
            ") SELECT * FROM (SELECT * FROM branch WHERE sequence < :beforeSequence " +
            "ORDER BY sequence DESC LIMIT :limit) ORDER BY sequence ASC",
    )
    suspend fun loadSelectedBranch(
        conversationId: String,
        beforeSequence: Long,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        "WITH RECURSIVE branch AS (" +
            "SELECT m.id, m.parentMessageId FROM messages m " +
            "INNER JOIN conversations c ON c.branchHeadMessageId = m.id WHERE c.id = :conversationId " +
            "UNION ALL SELECT parent.id, parent.parentMessageId FROM messages parent " +
            "INNER JOIN branch child ON child.parentMessageId = parent.id" +
            ") SELECT COUNT(*) FROM branch",
    )
    suspend fun countSelectedBranch(conversationId: String): Int

    @Query(
        "UPDATE messages SET content = :content, reasoningContent = :reasoningContent, " +
            "generationStatus = :status WHERE id = :id AND role = 'ASSISTANT'",
    )
    suspend fun updateAssistantOutput(
        id: String,
        content: String,
        reasoningContent: String,
        status: MessageGenerationStatus,
    ): Int

    @Query(
        "UPDATE messages SET generationStatus = 'INTERRUPTED' WHERE id IN (" +
            "SELECT assistantMessageId FROM generation_tasks WHERE state = 'RUNNING'" +
            ") AND generationStatus = 'STREAMING'",
    )
    suspend fun markRunningTaskOutputsInterrupted()

    @Query(
        "UPDATE messages SET generationStatus = 'CANCELLED' WHERE id IN (" +
            "SELECT assistantMessageId FROM generation_tasks WHERE state = 'QUEUED'" +
            ") AND generationStatus = 'STREAMING'",
    )
    suspend fun markQueuedTaskOutputsCancelled()
}

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attachment: AttachmentEntity)

    @Query(
        "SELECT a.* FROM attachments a INNER JOIN messages m ON m.id = a.messageId " +
            "WHERE m.conversationId = :conversationId ORDER BY m.sequence, a.id",
    )
    fun observeForConversation(conversationId: String): Flow<List<AttachmentEntity>>

    @Query(
        "SELECT a.* FROM attachments a INNER JOIN messages m ON m.id = a.messageId " +
            "WHERE m.conversationId = :conversationId ORDER BY m.sequence, a.id",
    )
    suspend fun loadForConversation(conversationId: String): List<AttachmentEntity>

    @Query(
        "SELECT a.localUri FROM attachments a INNER JOIN messages m ON m.id = a.messageId " +
            "WHERE m.conversationId = :conversationId AND a.localUri != ''",
    )
    suspend fun loadUrisForConversation(conversationId: String): List<String>

    @Query("SELECT localUri FROM attachments WHERE localUri != ''")
    suspend fun loadAllUris(): List<String>
}

data class GenerationTaskWithTitles(
    @Embedded val task: GenerationTaskEntity,
    val conversationTitle: String,
)

@Dao
interface GenerationTaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: GenerationTaskEntity)

    @Query("SELECT COALESCE(MAX(queueOrder), 0) FROM generation_tasks")
    suspend fun maxQueueOrder(): Long

    @Query("SELECT assistantMessageId FROM generation_tasks WHERE id = :taskId LIMIT 1")
    suspend fun assistantMessageId(taskId: String): String?

    @Query("SELECT * FROM generation_tasks WHERE id = :taskId LIMIT 1")
    suspend fun loadById(taskId: String): GenerationTaskEntity?

    @Query("SELECT COUNT(*) FROM generation_tasks WHERE conversationId = :conversationId AND state IN ('QUEUED', 'RUNNING')")
    suspend fun countActiveForConversation(conversationId: String): Int

    @Query(
        "SELECT t.*, c.title AS conversationTitle FROM generation_tasks t " +
            "INNER JOIN conversations c ON c.id = t.conversationId " +
            "ORDER BY t.createdAt DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int = 200): Flow<List<GenerationTaskWithTitles>>

    @Query("SELECT DISTINCT conversationId FROM generation_tasks WHERE isUnread = 1")
    fun observeUnreadConversationIds(): Flow<List<String>>

    @Query(
        "UPDATE generation_tasks SET state = 'RUNNING', startedAt = :now " +
            "WHERE id = :taskId AND state = 'QUEUED'",
    )
    suspend fun markRunning(taskId: String, now: Long): Int

    @Query(
        "UPDATE generation_tasks SET state = 'COMPLETED', completedAt = :now, isUnread = :isUnread " +
            "WHERE id = :taskId AND state = 'RUNNING'",
    )
    suspend fun markCompleted(taskId: String, now: Long, isUnread: Boolean): Int

    @Query(
        "UPDATE generation_tasks SET state = 'FAILED', completedAt = :now, errorCode = :errorCode " +
            "WHERE id = :taskId AND state = 'RUNNING'",
    )
    suspend fun markFailed(taskId: String, now: Long, errorCode: String): Int

    @Query(
        "UPDATE generation_tasks SET state = 'CANCELLED', completedAt = :now " +
            "WHERE id = :taskId AND state IN ('QUEUED', 'RUNNING')",
    )
    suspend fun markCancelled(taskId: String, now: Long): Int

    @Query("UPDATE generation_tasks SET isUnread = 0 WHERE conversationId = :conversationId AND isUnread = 1")
    suspend fun markConversationRead(conversationId: String)

    @Query(
        "UPDATE generation_tasks SET state = 'INTERRUPTED', completedAt = :now, errorCode = 'PROCESS_STOPPED' " +
            "WHERE state = 'RUNNING'",
    )
    suspend fun interruptRunning(now: Long): Int

    @Query(
        "UPDATE generation_tasks SET state = 'CANCELLED', completedAt = :now, errorCode = 'PROCESS_STOPPED' " +
            "WHERE state = 'QUEUED'",
    )
    suspend fun cancelQueuedAfterProcessStop(now: Long): Int
}

@Dao
interface UsageDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(usage: UsageEntity)

    @Query("SELECT COUNT(*) FROM usage WHERE generationTaskId = :taskId")
    suspend fun countForTask(taskId: String): Int
}
