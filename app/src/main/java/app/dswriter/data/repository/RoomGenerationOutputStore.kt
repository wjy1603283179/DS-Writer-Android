package app.dswriter.data.repository

import androidx.room.withTransaction
import app.dswriter.data.local.db.ConversationDao
import app.dswriter.data.local.db.DSWriterDatabase
import app.dswriter.data.local.db.MessageDao
import app.dswriter.data.local.db.MessageEntity
import app.dswriter.data.local.db.MessageGenerationStatus
import app.dswriter.data.local.db.MessageRole
import app.dswriter.domain.generation.GenerationOutputStore
import app.dswriter.domain.model.AppClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomGenerationOutputStore @Inject constructor(
    private val database: DSWriterDatabase,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val clock: AppClock,
) : GenerationOutputStore {
    override suspend fun createStreamingAssistantMessage(
        conversationId: String,
        parentMessageId: String,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = clock.currentTimeMillis()
        database.withTransaction {
            messageDao.insert(
                MessageEntity(
                    id = id,
                    conversationId = conversationId,
                    parentMessageId = parentMessageId,
                    role = MessageRole.ASSISTANT,
                    content = "",
                    sequence = messageDao.maxSequence(conversationId) + 1,
                    createdAt = now,
                    generationStatus = MessageGenerationStatus.STREAMING,
                ),
            )
            conversationDao.updateBranchHead(conversationId, id, now)
        }
        return id
    }

    override suspend fun persist(
        messageId: String,
        content: String,
        reasoningContent: String,
        status: MessageGenerationStatus,
    ) {
        check(messageDao.updateAssistantOutput(messageId, content, reasoningContent, status) == 1)
    }
}
