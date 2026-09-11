package app.dswriter.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.dswriter.data.local.db.DSWriterDatabase
import app.dswriter.domain.model.AppClock
import app.dswriter.domain.model.ImageAttachmentPreparer
import app.dswriter.domain.model.PreparedImageAttachment
import app.dswriter.data.local.db.MessageGenerationStatus
import java.util.Collections
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomConversationRepositoryTest {
    private lateinit var database: DSWriterDatabase
    private lateinit var repository: RoomConversationRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), DSWriterDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomConversationRepository(
            database, database.appStateDao(), database.conversationDao(), database.messageDao(),
            database.attachmentDao(), database.generationTaskDao(), FakeImagePreparer(), AppClock { 100 },
        )
    }

    @After fun tearDown() = database.close()

    @Test fun namesAreAtomicMonotonicAndNeverReused() = runTest {
        val ids = (1..20).map { async { repository.create() } }.awaitAll()
        val titles = repository.recentConversations.first().map { it.title }.toSet()
        assertEquals(20, titles.size)
        assertEquals((1..20).map { "新对话 $it" }.toSet(), titles)
        repository.rename(ids.first(), "已重命名")
        repository.moveToTrash(ids[1])
        repository.deletePermanently(ids[1])
        val next = repository.create()
        assertEquals("新对话 21", repository.observeConversation(next).first()?.title)
    }

    @Test fun titleSearchIsBoundedAndDoesNotSearchMessages() = runTest {
        val id = repository.create()
        repository.rename(id, "人物讨论")
        repository.appendMessage(id, null, app.dswriter.domain.model.ConversationRole.USER, "不应匹配的正文")
        assertEquals(id, repository.searchConversations("人物").first().single().id)
        assertTrue(repository.searchConversations("不应匹配").first().isEmpty())
    }

    @Test fun roomPersistsAndLoadsVeryLongReasoningExactly() = runTest {
        val conversationId = repository.create()
        val userId = repository.appendMessage(
            conversationId, null, app.dswriter.domain.model.ConversationRole.USER, "请回答",
        )
        val outputStore = RoomGenerationOutputStore(
            database, database.conversationDao(), database.messageDao(), AppClock { 200 },
        )
        val assistantId = outputStore.createStreamingAssistantMessage(conversationId, userId)
        val reasoning = "长思考🙂".repeat(80_000)

        outputStore.persist(assistantId, "最终回答", reasoning, MessageGenerationStatus.COMPLETE)

        val assistant = repository.observeSelectedBranch(conversationId).first().last()
        assertEquals("最终回答", assistant.content)
        assertEquals(reasoning, assistant.reasoningContent)
        assertEquals(reasoning.length, assistant.reasoningCharacterCount)
    }
}

private class FakeImagePreparer : ImageAttachmentPreparer {
    val discarded = Collections.synchronizedList(mutableListOf<String>())
    override suspend fun prepare(sourceUri: String): PreparedImageAttachment = error("not used")
    override suspend fun discard(localUri: String) { discarded += localUri }
    override suspend fun discardAll() = Unit
}
