package app.dswriter.domain.generation

import app.dswriter.data.local.db.MessageGenerationStatus
import app.dswriter.data.remote.ChatStreamingClient
import app.dswriter.data.remote.FinishReason
import app.dswriter.data.remote.GenerationStreamEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue

class GenerationExecutorTest {
    @Test
    fun `accumulates chunks and persists once on fast completion`() = runTest {
        val time = MutableTimeSource()
        val store = RecordingOutputStore()
        val client = FakeStreamingClient {
            flow {
                repeat(20) {
                    time.now += 10_000_000
                    emit(GenerationStreamEvent.ContentDelta("字"))
                }
                emit(GenerationStreamEvent.ReasoningDelta("思考"))
                emit(GenerationStreamEvent.Completed(FinishReason.STOP, null))
            }
        }
        val executor = GenerationExecutor(client, store, time)

        val progress = executor.execute(snapshot(), "key", "assistant").toList()

        assertEquals(1, store.writes.size)
        assertEquals(MessageGenerationStatus.COMPLETE, store.writes.single().status)
        assertEquals("字".repeat(20), store.writes.single().content)
        assertEquals("思考", store.writes.single().reasoning)
        assertEquals(true, progress.last().isComplete)
    }

    @Test
    fun `cancellation flushes partial output as cancelled`() = runTest {
        val started = CompletableDeferred<Unit>()
        val store = RecordingOutputStore()
        val client = FakeStreamingClient {
            flow {
                emit(GenerationStreamEvent.ContentDelta("部分"))
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val executor = GenerationExecutor(client, store, MutableTimeSource())
        val job = launch { executor.execute(snapshot(), "key", "assistant").collect {} }
        started.await()

        job.cancelAndJoin()

        assertEquals(MessageGenerationStatus.CANCELLED, store.writes.last().status)
        assertEquals("部分", store.writes.last().content)
    }

    @Test
    fun `thousands of token chunks remain batched and use one mutable accumulation path`() = runTest {
        val time = MutableTimeSource()
        val store = RecordingOutputStore()
        val client = FakeStreamingClient {
            flow {
                repeat(3_000) {
                    time.now += 1_000_000
                    emit(GenerationStreamEvent.ContentDelta("字"))
                }
                emit(GenerationStreamEvent.Completed(FinishReason.STOP, null))
            }
        }
        val executor = GenerationExecutor(client, store, time)

        val progress = executor.execute(snapshot(), "key", "assistant").toList()

        assertEquals("字".repeat(3_000), progress.last().content)
        assertTrue(progress.size <= 61)
        assertTrue(store.writes.size <= 11)
        assertEquals(MessageGenerationStatus.COMPLETE, store.writes.last().status)
    }

    @Test
    fun `very long reasoning uses a bounded live preview while persisting every character`() = runTest {
        val time = MutableTimeSource()
        val store = RecordingOutputStore()
        val reasoningChunk = "深度思考🙂".repeat(500)
        val client = FakeStreamingClient {
            flow {
                repeat(100) {
                    time.now += 10_000_000
                    emit(GenerationStreamEvent.ReasoningDelta(reasoningChunk))
                }
                emit(GenerationStreamEvent.ContentDelta("最终回答"))
                emit(GenerationStreamEvent.Completed(FinishReason.STOP, null))
            }
        }

        val progress = GenerationExecutor(client, store, time)
            .execute(snapshot(), "key", "assistant").toList()
        val expected = reasoningChunk.repeat(100)

        assertEquals(expected, store.writes.last().reasoning)
        assertEquals(expected.length, progress.last().reasoningCharacterCount)
        assertTrue(progress.last().reasoningIsPreview)
        assertTrue(progress.maxOf { it.reasoningContent.length } <= 12_010)
        assertEquals("最终回答", progress.last().content)
    }

    private fun snapshot() = GenerationRequestSnapshot(
        "https://provider.test/v1",
        "any-model",
        listOf(RequestMessageSnapshot(RequestRole.USER, "继续")),
    )
}

private class FakeStreamingClient(
    private val events: () -> Flow<GenerationStreamEvent>,
) : ChatStreamingClient {
    override fun stream(snapshot: GenerationRequestSnapshot, apiKey: String): Flow<GenerationStreamEvent> = events()
}

private class MutableTimeSource(var now: Long = 0) : MonotonicTimeSource {
    override fun nanoTime(): Long = now
}

private class RecordingOutputStore : GenerationOutputStore {
    data class Write(
        val content: String,
        val reasoning: String,
        val status: MessageGenerationStatus,
    )

    val writes = mutableListOf<Write>()

    override suspend fun createStreamingAssistantMessage(
        conversationId: String,
        parentMessageId: String,
    ): String = "assistant"

    override suspend fun persist(
        messageId: String,
        content: String,
        reasoningContent: String,
        status: MessageGenerationStatus,
    ) {
        writes += Write(content, reasoningContent, status)
    }
}
