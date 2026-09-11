package app.dswriter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AppStateEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        GenerationTaskEntity::class,
        AttachmentEntity::class,
        UsageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DSWriterDatabase : RoomDatabase() {
    abstract fun appStateDao(): AppStateDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun generationTaskDao(): GenerationTaskDao
    abstract fun usageDao(): UsageDao
    abstract fun exportDao(): ExportDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE messages ADD COLUMN reasoningContent TEXT NOT NULL DEFAULT ''",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS attachments")
        db.execSQL("DROP TABLE IF EXISTS usage")
        db.execSQL("DROP TABLE IF EXISTS generation_tasks")
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS conversations")
        db.execSQL("DROP TABLE IF EXISTS projects")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS app_state (" +
                "id INTEGER NOT NULL, nextConversationNumber INTEGER NOT NULL, " +
                "migrationNoticePending INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        db.execSQL("INSERT INTO app_state VALUES (1, 1, 1)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS conversations (" +
                "id TEXT NOT NULL, title TEXT NOT NULL, selectedModelId TEXT NOT NULL, " +
                "branchHeadMessageId TEXT, draft TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL, deletedAt INTEGER, PRIMARY KEY(id))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversations_deletedAt_updatedAt " +
                "ON conversations(deletedAt, updatedAt)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversations_branchHeadMessageId " +
                "ON conversations(branchHeadMessageId)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS messages (" +
                "id TEXT NOT NULL, conversationId TEXT NOT NULL, parentMessageId TEXT, role TEXT NOT NULL, " +
                "content TEXT NOT NULL, reasoningContent TEXT NOT NULL, sequence INTEGER NOT NULL, " +
                "createdAt INTEGER NOT NULL, generationStatus TEXT NOT NULL, PRIMARY KEY(id), " +
                "FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_messages_conversationId_sequence ON messages(conversationId, sequence)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_parentMessageId ON messages(parentMessageId)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS generation_tasks (" +
                "id TEXT NOT NULL, conversationId TEXT NOT NULL, userMessageId TEXT NOT NULL, " +
                "assistantMessageId TEXT, requestSnapshotJson TEXT NOT NULL, modelId TEXT NOT NULL, " +
                "baseUrl TEXT NOT NULL, state TEXT NOT NULL, queueOrder INTEGER NOT NULL, " +
                "createdAt INTEGER NOT NULL, startedAt INTEGER, completedAt INTEGER, errorCode TEXT, " +
                "isUnread INTEGER NOT NULL, PRIMARY KEY(id), " +
                "FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_generation_tasks_conversationId_createdAt ON generation_tasks(conversationId, createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_generation_tasks_state_queueOrder ON generation_tasks(state, queueOrder)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_generation_tasks_conversationId_isUnread ON generation_tasks(conversationId, isUnread)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS attachments (" +
                "id TEXT NOT NULL, messageId TEXT NOT NULL, localUri TEXT NOT NULL, mimeType TEXT NOT NULL, " +
                "displayName TEXT NOT NULL, width INTEGER, height INTEGER, sizeBytes INTEGER NOT NULL, " +
                "preparationState TEXT NOT NULL, PRIMARY KEY(id), " +
                "FOREIGN KEY(messageId) REFERENCES messages(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_messageId ON attachments(messageId)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS usage (" +
                "id TEXT NOT NULL, generationTaskId TEXT NOT NULL, assistantMessageId TEXT NOT NULL, " +
                "promptTokens INTEGER NOT NULL, completionTokens INTEGER NOT NULL, totalTokens INTEGER NOT NULL, " +
                "promptCacheHitTokens INTEGER, promptCacheMissTokens INTEGER, PRIMARY KEY(id), " +
                "FOREIGN KEY(generationTaskId) REFERENCES generation_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_usage_generationTaskId ON usage(generationTaskId)")
    }
}
