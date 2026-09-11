package app.dswriter.data.repository

import androidx.room.withTransaction
import app.dswriter.data.local.db.AppStateDao
import app.dswriter.data.local.db.AppStateEntity
import app.dswriter.data.local.db.AttachmentDao
import app.dswriter.data.local.db.AttachmentEntity
import app.dswriter.data.local.db.AttachmentPreparationState
import app.dswriter.data.local.db.ConversationDao
import app.dswriter.data.local.db.ConversationEntity
import app.dswriter.data.local.db.DSWriterDatabase
import app.dswriter.data.local.db.GenerationTaskDao
import app.dswriter.data.local.db.MessageDao
import app.dswriter.data.local.db.MessageEntity
import app.dswriter.data.local.db.MessageRole
import app.dswriter.domain.model.ActiveGenerationDeletionException
import app.dswriter.domain.model.AppClock
import app.dswriter.domain.model.AttachmentState
import app.dswriter.domain.model.ConversationAttachment
import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationMessageStatus
import app.dswriter.domain.model.ConversationRepository
import app.dswriter.domain.model.ConversationRole
import app.dswriter.domain.model.ConversationSummary
import app.dswriter.domain.model.ImageAttachmentPreparer
import app.dswriter.domain.model.PreparedImageAttachment
import app.dswriter.domain.model.TrashedConversation
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class RoomConversationRepository @Inject constructor(
    private val database: DSWriterDatabase,
    private val appStateDao: AppStateDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
    private val taskDao: GenerationTaskDao,
    private val imagePreparer: ImageAttachmentPreparer,
    private val clock: AppClock,
) : ConversationRepository {
    override val recentConversations: Flow<List<ConversationSummary>> =
        conversationDao.observeRecent().map { rows -> rows.map { it.toModel() } }

    override val trashedConversations: Flow<List<TrashedConversation>> =
        conversationDao.observeTrashed().map { rows ->
            rows.map { TrashedConversation(it.id, it.title, requireNotNull(it.deletedAt)) }
        }

    override fun searchConversations(query: String): Flow<List<ConversationSummary>> {
        val normalized = query.trim()
        return if (normalized.isEmpty()) recentConversations else
            conversationDao.searchByTitle(normalized).map { rows -> rows.map { it.toModel() } }
    }

    override fun observeConversation(id: String): Flow<ConversationSummary?> =
        conversationDao.observeById(id).map { row -> row?.takeIf { it.deletedAt == null }?.toModel() }

    override fun observeSelectedBranch(id: String, limit: Int): Flow<List<ConversationMessage>> {
        require(limit in 1..200)
        return combine(messageDao.observeSelectedBranch(id, limit), attachmentDao.observeForConversation(id)) {
                rows, attachments -> rows.toModels(attachments)
        }
    }

    override suspend fun create(): String = database.withTransaction {
        appStateDao.ensure(AppStateEntity())
        val number = requireNotNull(appStateDao.get()).nextConversationNumber
        check(appStateDao.incrementConversationNumber() == 1)
        val now = clock.currentTimeMillis()
        UUID.randomUUID().toString().also { id ->
            conversationDao.insert(
                ConversationEntity(id, "新对话 $number", LEGACY_MODEL_COLUMN_VALUE, createdAt = now, updatedAt = now),
            )
        }
    }

    override suspend fun rename(id: String, title: String) {
        val normalized = title.trim().also { require(it.isNotEmpty()) }
        check(conversationDao.rename(id, normalized, clock.currentTimeMillis()) == 1)
    }

    override suspend fun updateDraft(id: String, draft: String) {
        check(conversationDao.updateDraft(id, draft, clock.currentTimeMillis()) == 1)
    }

    override suspend fun updateSelectedModel(id: String, modelId: String) {
        // The selectable set depends on the configured local model, which this layer does not
        // own. The UI offers only resolvable ids and request construction re-validates the id,
        // so here it is enough to reject an unusable value.
        require(modelId.isNotBlank()) { "A model id is required" }
        check(conversationDao.updateSelectedModel(id, modelId, clock.currentTimeMillis()) == 1)
    }

    override suspend fun moveToTrash(id: String) {
        check(conversationDao.softDelete(id, clock.currentTimeMillis()) == 1)
    }

    override suspend fun restore(id: String) {
        check(conversationDao.restore(id, clock.currentTimeMillis()) == 1)
    }

    override suspend fun deletePermanently(id: String) {
        if (taskDao.countActiveForConversation(id) > 0) throw ActiveGenerationDeletionException()
        val uris = attachmentDao.loadUrisForConversation(id)
        check(conversationDao.deletePermanently(id) == 1)
        uris.forEach { uri -> runCatching { imagePreparer.discard(uri) } }
    }

    override suspend fun appendMessage(
        conversationId: String,
        parentMessageId: String?,
        role: ConversationRole,
        content: String,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = clock.currentTimeMillis()
        database.withTransaction {
            val nextSequence = messageDao.maxSequence(conversationId) + 1
            messageDao.insert(
                MessageEntity(
                    id = id,
                    conversationId = conversationId,
                    parentMessageId = parentMessageId,
                    role = if (role == ConversationRole.USER) MessageRole.USER else MessageRole.ASSISTANT,
                    content = content,
                    sequence = nextSequence,
                    createdAt = now,
                ),
            )
            conversationDao.updateBranchHead(conversationId, id, now)
        }
        return id
    }

    override suspend fun addPreparedImage(messageId: String, image: PreparedImageAttachment): String {
        require(image.mimeType in SUPPORTED_IMAGE_TYPES && image.width > 0 && image.height > 0 && image.sizeBytes > 0)
        return UUID.randomUUID().toString().also { id ->
            attachmentDao.insert(
                AttachmentEntity(
                    id, messageId, image.localUri, image.mimeType, image.displayName,
                    image.width, image.height, image.sizeBytes, AttachmentPreparationState.READY,
                ),
            )
        }
    }

    override suspend fun loadMessagePage(conversationId: String, beforeSequence: Long, limit: Int): List<ConversationMessage> {
        require(limit in 1..200)
        return messageDao.loadPage(conversationId, beforeSequence, limit).asReversed()
            .toModels(attachmentDao.loadForConversation(conversationId))
    }

    override suspend fun loadSelectedBranch(id: String, beforeSequence: Long, limit: Int): List<ConversationMessage> {
        require(limit in 1..200)
        return messageDao.loadSelectedBranch(id, beforeSequence, limit)
            .toModels(attachmentDao.loadForConversation(id))
    }

    override suspend fun countSelectedBranch(id: String): Int = messageDao.countSelectedBranch(id)

    override suspend fun consumeMigrationNotice(): Boolean {
        appStateDao.ensure(AppStateEntity())
        if (appStateDao.get()?.migrationNoticePending != true) return false
        imagePreparer.discardAll()
        return appStateDao.consumeMigrationNotice() == 1
    }

    private fun List<MessageEntity>.toModels(attachments: List<AttachmentEntity>): List<ConversationMessage> {
        val byMessage = attachments.groupBy(AttachmentEntity::messageId)
        return map { it.toModel(byMessage[it.id].orEmpty()) }
    }

    private fun MessageEntity.toModel(attachments: List<AttachmentEntity>) = ConversationMessage(
        id, conversationId, parentMessageId,
        if (role == MessageRole.USER) ConversationRole.USER else ConversationRole.ASSISTANT,
        content, reasoningContent, ConversationMessageStatus.valueOf(generationStatus.name), sequence, createdAt,
        attachments.map { attachment ->
            ConversationAttachment(
                attachment.id, attachment.messageId, attachment.localUri, attachment.mimeType,
                attachment.displayName, requireNotNull(attachment.width), requireNotNull(attachment.height),
                attachment.sizeBytes, AttachmentState.valueOf(attachment.preparationState.name),
            )
        },
    )

    private fun ConversationEntity.toModel() = ConversationSummary(id, title, selectedModelId, draft, updatedAt)

    private companion object {
        /**
         * The conversation table still has a model column from an earlier version. The provider is
         * now application-wide, so nothing reads it; new rows just carry a neutral placeholder.
         */
        const val LEGACY_MODEL_COLUMN_VALUE = ""
        val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
