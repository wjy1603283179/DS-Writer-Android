package app.dswriter.data.local.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ExportDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt, id")
    suspend fun loadConversations(): List<ConversationEntity>

    @Query("SELECT * FROM messages ORDER BY conversationId, sequence")
    suspend fun loadMessages(): List<MessageEntity>
}
