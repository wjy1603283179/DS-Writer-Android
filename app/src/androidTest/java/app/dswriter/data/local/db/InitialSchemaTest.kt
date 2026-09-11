package app.dswriter.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InitialSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), DSWriterDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationTwoToThreeClearsLegacyContentAndCreatesPendingNotice() {
        helper.createDatabase("migration-2-3", 2).use(::seedLegacyData)
        helper.runMigrationsAndValidate("migration-2-3", 3, true, MIGRATION_2_3).use { database ->
            assertEquals(0, database.scalar("SELECT COUNT(*) FROM conversations"))
            assertEquals(0, database.scalar("SELECT COUNT(*) FROM messages"))
            assertEquals(1, database.scalar("SELECT nextConversationNumber FROM app_state WHERE id = 1"))
            assertEquals(1, database.scalar("SELECT migrationNoticePending FROM app_state WHERE id = 1"))
            assertFalse(database.tableNames().contains("projects"))
        }
    }

    @Test
    fun migrationOneToTwoToThreeIsExplicitAndValidated() {
        helper.createDatabase("migration-1-3", 1).use { database ->
            database.execSQL("INSERT INTO projects VALUES ('p', '项目', 1, 1, NULL)")
            database.execSQL("INSERT INTO conversations VALUES ('c', 'p', '对话', 'deepseek-v4-flash', NULL, '', 1, 1, NULL)")
            database.execSQL(
                "INSERT INTO messages (id, conversationId, parentMessageId, role, content, sequence, createdAt, generationStatus) " +
                    "VALUES ('m', 'c', NULL, 'USER', '原文🙂', 1, 1, 'COMPLETE')",
            )
        }
        helper.runMigrationsAndValidate("migration-1-3", 3, true, MIGRATION_1_2, MIGRATION_2_3).use { database ->
            assertEquals(0, database.scalar("SELECT COUNT(*) FROM conversations"))
            assertTrue(database.tableNames().containsAll(setOf("app_state", "conversations", "messages", "generation_tasks", "attachments", "usage")))
            assertFalse(database.tableNames().contains("projects"))
        }
    }

    private fun seedLegacyData(database: SupportSQLiteDatabase) {
        database.execSQL("INSERT INTO projects VALUES ('p', '项目', 1, 1, NULL)")
        database.execSQL("INSERT INTO conversations VALUES ('c', 'p', '对话', 'deepseek-v4-flash', NULL, '', 1, 1, NULL)")
        database.execSQL(
            "INSERT INTO messages VALUES ('m', 'c', NULL, 'USER', '正文', '', 1, 1, 'COMPLETE')",
        )
    }

    private fun SupportSQLiteDatabase.scalar(sql: String): Int = query(sql).use { cursor ->
        check(cursor.moveToFirst()); cursor.getInt(0)
    }

    private fun SupportSQLiteDatabase.tableNames(): Set<String> = query(
        "SELECT name FROM sqlite_master WHERE type = 'table'",
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
}
