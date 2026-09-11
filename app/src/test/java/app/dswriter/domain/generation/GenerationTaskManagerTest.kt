package app.dswriter.domain.generation

import app.dswriter.data.local.db.MessageGenerationStatus
import app.dswriter.data.remote.ApiUsage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationTaskManagerTest {
    @Test
    fun `runs three queues later work in FIFO order and isolates failure`() = runBlocking {
        val runner = ControlledRunner()
        val repository = FakeTaskRepository()
        val foreground = FakeForegroundController()
        val manager = GenerationTaskManager(runner, repository, FakeOutputStore(), foreground)

        (1..5).forEach { index ->
            assertTrue(manager.submit(submission(index), "secret"))
        }
        assertEquals(1, foreground.startCount.get())
        val initiallyStarted = (1..3).map { withTimeout(2_000) { runner.started.receive() } }.toSet()
        assertEquals(setOf("assistant-1", "assistant-2", "assistant-3"), initiallyStarted)
        assertEquals(null, withTimeoutOrNull(150) { runner.started.receive() })
        assertEquals(GenerationTaskStatus.QUEUED, repository.statuses.getValue("task-4"))

        runner.finish("assistant-1")
        assertEquals("assistant-4", withTimeout(2_000) { runner.started.receive() })
        runner.finish("assistant-2", IllegalStateException("isolated"))
        assertEquals("assistant-5", withTimeout(2_000) { runner.started.receive() })
        waitUntil { repository.statuses["task-2"] == GenerationTaskStatus.FAILED }
        assertEquals(GenerationTaskStatus.RUNNING, repository.statuses["task-3"])

        runner.finish("assistant-3")
        runner.finish("assistant-4")
        runner.finish("assistant-5")
        waitUntil { repository.statuses.values.count { it == GenerationTaskStatus.COMPLETED } == 4 }
        waitUntil { manager.states.value.values.none { it.status in setOf(GenerationTaskStatus.QUEUED, GenerationTaskStatus.RUNNING) } }

        assertTrue(manager.submit(submission(6), "secret"))
        assertEquals(2, foreground.startCount.get())
        assertEquals("assistant-6", withTimeout(2_000) { runner.started.receive() })
        runner.finish("assistant-6")
        waitUntil { repository.statuses["task-6"] == GenerationTaskStatus.COMPLETED }
    }

    @Test
    fun `cancels one running task without cancelling its sibling`() = runBlocking {
        val runner = ControlledRunner()
        val repository = FakeTaskRepository()
        val manager = GenerationTaskManager(runner, repository, FakeOutputStore(), FakeForegroundController())
        manager.submit(submission(1), "secret")
        manager.submit(submission(2), "secret")
        repeat(2) { withTimeout(2_000) { runner.started.receive() } }

        manager.cancelTask("task-1")
        waitUntil { repository.statuses["task-1"] == GenerationTaskStatus.CANCELLED }
        assertEquals(GenerationTaskStatus.RUNNING, repository.statuses["task-2"])
        runner.finish("assistant-2")
        waitUntil { repository.statuses["task-2"] == GenerationTaskStatus.COMPLETED }
    }

    @Test
    fun `completion is unread only when conversation is not visible`() = runBlocking {
        val runner = ControlledRunner()
        val repository = FakeTaskRepository()
        val manager = GenerationTaskManager(runner, repository, FakeOutputStore(), FakeForegroundController())
        manager.setConversationVisible("conversation-1", true)
        manager.submit(submission(1), "secret")
        manager.submit(submission(2), "secret")
        repeat(2) { withTimeout(2_000) { runner.started.receive() } }

        runner.finish("assistant-1")
        runner.finish("assistant-2")
        waitUntil { repository.statuses.values.all { it == GenerationTaskStatus.COMPLETED } }
        assertFalse(repository.unread.getValue("task-1"))
        assertTrue(repository.unread.getValue("task-2"))
    }

    private fun submission(index: Int) = GenerationTaskSubmission(
        conversationId = "conversation-$index",
        userMessageId = "user-$index",
        assistantMessageId = "assistant-$index",
        snapshot = GenerationRequestSnapshot(
            baseUrl = "https://provider.test/v1",
            modelId = "any-model",
            messages = listOf(RequestMessageSnapshot(RequestRole.USER, "继续$index")),
        ),
    )

    private suspend fun waitUntil(predicate: () -> Boolean) = withTimeout(2_000) {
        while (!predicate()) delay(10)
    }
}

private class ControlledRunner : GenerationRunner {
    val started = Channel<String>(Channel.UNLIMITED)
    private val outcomes = ConcurrentHashMap<String, CompletableDeferred<Throwable?>>()

    override fun execute(
        snapshot: GenerationRequestSnapshot,
        apiKey: String,
        assistantMessageId: String,
    ): Flow<GenerationProgress> = flow {
        started.send(assistantMessageId)
        val failure = outcomes.computeIfAbsent(assistantMessageId) { CompletableDeferred() }.await()
        if (failure != null) throw failure
        emit(GenerationProgress("完成", "", true))
    }

    fun finish(assistantMessageId: String, failure: Throwable? = null) {
        outcomes.computeIfAbsent(assistantMessageId) { CompletableDeferred() }.complete(failure)
    }
}

private class FakeTaskRepository : GenerationTaskRepository {
    private val ids = AtomicInteger()
    override val recentTasks = MutableStateFlow<List<GenerationTaskSummary>>(emptyList())
    override val unreadConversationIds = MutableStateFlow<Set<String>>(emptySet())
    val statuses = ConcurrentHashMap<String, GenerationTaskStatus>()
    val unread = ConcurrentHashMap<String, Boolean>()

    override suspend fun createQueued(submission: GenerationTaskSubmission): String =
        "task-${ids.incrementAndGet()}".also { statuses[it] = GenerationTaskStatus.QUEUED }

    override suspend fun markRunning(taskId: String) { statuses[taskId] = GenerationTaskStatus.RUNNING }
    override suspend fun markCompleted(taskId: String, usage: ApiUsage?, isUnread: Boolean) {
        statuses[taskId] = GenerationTaskStatus.COMPLETED
        unread[taskId] = isUnread
    }
    override suspend fun markFailed(taskId: String, errorCode: String) {
        statuses[taskId] = GenerationTaskStatus.FAILED
    }
    override suspend fun markCancelled(taskId: String) { statuses[taskId] = GenerationTaskStatus.CANCELLED }
    override suspend fun markConversationRead(conversationId: String) = Unit
    override suspend fun recoverAfterProcessStop() = Unit
}

private class FakeOutputStore : GenerationOutputStore {
    override suspend fun createStreamingAssistantMessage(conversationId: String, parentMessageId: String) = ""
    override suspend fun persist(
        messageId: String,
        content: String,
        reasoningContent: String,
        status: MessageGenerationStatus,
    ) = Unit
}

private class FakeForegroundController : GenerationForegroundController {
    val startCount = AtomicInteger()
    override fun start() { startCount.incrementAndGet() }
}
