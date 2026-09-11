package app.dswriter.domain.model

import kotlinx.coroutines.flow.Flow

class ActiveGenerationDeletionException : IllegalStateException("Active generation prevents permanent deletion")

interface ConversationRepository {
    val recentConversations: Flow<List<ConversationSummary>>
    val trashedConversations: Flow<List<TrashedConversation>>
    fun searchConversations(query: String): Flow<List<ConversationSummary>>
    fun observeConversation(id: String): Flow<ConversationSummary?>
    fun observeSelectedBranch(id: String, limit: Int = 100): Flow<List<ConversationMessage>>
    suspend fun create(): String
    suspend fun rename(id: String, title: String)
    suspend fun updateDraft(id: String, draft: String)
    suspend fun updateSelectedModel(id: String, modelId: String)
    suspend fun moveToTrash(id: String)
    suspend fun restore(id: String)
    suspend fun deletePermanently(id: String)
    suspend fun appendMessage(conversationId: String, parentMessageId: String?, role: ConversationRole, content: String): String
    suspend fun addPreparedImage(messageId: String, image: PreparedImageAttachment): String
    suspend fun loadMessagePage(conversationId: String, beforeSequence: Long = Long.MAX_VALUE, limit: Int = 50): List<ConversationMessage>
    suspend fun loadSelectedBranch(id: String, beforeSequence: Long = Long.MAX_VALUE, limit: Int = 200): List<ConversationMessage>
    suspend fun countSelectedBranch(id: String): Int
    suspend fun consumeMigrationNotice(): Boolean
}

interface ImageAttachmentPreparer {
    suspend fun prepare(sourceUri: String): PreparedImageAttachment
    suspend fun discard(localUri: String)
    suspend fun discardAll()
}
