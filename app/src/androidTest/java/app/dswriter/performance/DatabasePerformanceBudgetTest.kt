package app.dswriter.performance

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.dswriter.data.local.db.ConversationEntity
import app.dswriter.data.local.db.DSWriterDatabase
import app.dswriter.data.local.db.MessageEntity
import app.dswriter.data.local.db.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabasePerformanceBudgetTest {
    private lateinit var database: DSWriterDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DSWriterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun tenThousandMessageConversationUsesBoundedIndexedReads() = runTest {
        val body = "长篇消息🙂".repeat(24)
        val messages = (1..MESSAGE_COUNT).map { index ->
            MessageEntity(
                id = "message-$index",
                conversationId = CONVERSATION_ID,
                parentMessageId = if (index == 1) null else "message-${index - 1}",
                role = if (index % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
                content = "$index-$body",
                sequence = index.toLong(),
                createdAt = index.toLong(),
            )
        }
        val seedMs = measuredMillis {
            database.withTransaction {
                database.conversationDao().insert(
                    ConversationEntity(
                        CONVERSATION_ID, "大型对话", "deepseek-v4-flash",
                        "message-$MESSAGE_COUNT", "", 1, 1,
                    ),
                )
                messages.forEach { database.messageDao().insert(it) }
            }
        }

        var page = emptyList<MessageEntity>()
        val pageMs = measuredMillis {
            repeat(20) {
                page = database.messageDao().loadPage(CONVERSATION_ID, limit = 50)
            }
        }
        var branch = emptyList<MessageEntity>()
        val branchMs = measuredMillis {
            repeat(3) {
                branch = database.messageDao().loadSelectedBranch(
                    CONVERSATION_ID,
                    Long.MAX_VALUE,
                    200,
                )
            }
        }
        var count = 0
        val countMs = measuredMillis { count = database.messageDao().countSelectedBranch(CONVERSATION_ID) }
        val plan = queryPlan()

        println("PERF_DB seedMs=$seedMs page20Ms=$pageMs branch3Ms=$branchMs countMs=$countMs")
        assertEquals(50, page.size)
        assertEquals(200, branch.size)
        assertEquals(MESSAGE_COUNT, count)
        assertTrue("Message page query must use the conversation/sequence index: $plan", plan.contains("index_messages_conversationId_sequence"))
        assertTrue("20 page reads exceeded budget: ${pageMs}ms", pageMs <= PAGE_READ_BUDGET_MS)
        assertTrue("3 branch reads exceeded budget: ${branchMs}ms", branchMs <= BRANCH_READ_BUDGET_MS)
        assertTrue("Branch count exceeded budget: ${countMs}ms", countMs <= COUNT_BUDGET_MS)
    }

    private fun queryPlan(): String {
        val cursor = database.openHelper.readableDatabase.query(
            "EXPLAIN QUERY PLAN SELECT * FROM messages WHERE conversationId = ? " +
                "AND sequence < ? ORDER BY sequence DESC LIMIT ?",
            arrayOf<Any>(CONVERSATION_ID, Long.MAX_VALUE, 50),
        )
        return cursor.use {
            buildString {
                while (it.moveToNext()) appendLine(it.getString(3))
            }
        }
    }

    private inline fun measuredMillis(block: () -> Unit): Long {
        val start = SystemClock.elapsedRealtimeNanos()
        block()
        return (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
    }

    private companion object {
        const val CONVERSATION_ID = "conversation"
        const val MESSAGE_COUNT = 10_000
        const val PAGE_READ_BUDGET_MS = 1_000L
        const val BRANCH_READ_BUDGET_MS = 2_000L
        const val COUNT_BUDGET_MS = 1_000L
    }
}
