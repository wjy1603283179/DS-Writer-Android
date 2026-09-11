package app.dswriter.domain.generation

import app.dswriter.data.local.db.MessageGenerationStatus
import app.dswriter.data.remote.ApiUsage
import app.dswriter.data.remote.ChatStreamingClient
import app.dswriter.data.remote.FinishReason
import app.dswriter.data.remote.GenerationStreamEvent
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

data class GenerationProgress(
    val content: String,
    val reasoningContent: String,
    val isComplete: Boolean,
    val finishReason: FinishReason? = null,
    val usage: ApiUsage? = null,
    val reasoningCharacterCount: Int = reasoningContent.length,
    val reasoningIsPreview: Boolean = false,
)

interface GenerationRunner {
    fun execute(
        snapshot: GenerationRequestSnapshot,
        apiKey: String,
        assistantMessageId: String,
    ): Flow<GenerationProgress>
}

class GenerationExecutor @Inject constructor(
    private val client: ChatStreamingClient,
    private val outputStore: GenerationOutputStore,
    private val timeSource: MonotonicTimeSource,
) : GenerationRunner {
    override fun execute(
        snapshot: GenerationRequestSnapshot,
        apiKey: String,
        assistantMessageId: String,
    ): Flow<GenerationProgress> = flow {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var lastUiUpdate = timeSource.nanoTime()
        var lastPersistence = lastUiUpdate
        var completed = false

        suspend fun persist(status: MessageGenerationStatus) {
            outputStore.persist(assistantMessageId, content.toString(), reasoning.toString(), status)
            lastPersistence = timeSource.nanoTime()
        }

        suspend fun emitProgress(force: Boolean = false) {
            val now = timeSource.nanoTime()
            if (force || now - lastUiUpdate >= UI_UPDATE_INTERVAL_NANOS) {
                val reasoningPreview = reasoning.toUiPreview()
                emit(
                    GenerationProgress(
                        content = content.toString(),
                        reasoningContent = reasoningPreview.text,
                        isComplete = false,
                        reasoningCharacterCount = reasoning.length,
                        reasoningIsPreview = reasoningPreview.isPreview,
                    ),
                )
                lastUiUpdate = now
            }
            val persistenceInterval = if (reasoning.length >= LONG_REASONING_THRESHOLD) {
                LONG_REASONING_PERSIST_INTERVAL_NANOS
            } else PERSIST_INTERVAL_NANOS
            if (now - lastPersistence >= persistenceInterval) persist(MessageGenerationStatus.STREAMING)
        }

        try {
            client.stream(snapshot, apiKey).collect { event ->
                when (event) {
                    is GenerationStreamEvent.ContentDelta -> {
                        content.append(event.text)
                        emitProgress()
                    }
                    is GenerationStreamEvent.ReasoningDelta -> {
                        reasoning.append(event.text)
                        emitProgress()
                    }
                    is GenerationStreamEvent.Completed -> {
                        persist(MessageGenerationStatus.COMPLETE)
                        completed = true
                        val reasoningPreview = reasoning.toUiPreview()
                        emit(
                            GenerationProgress(
                                content.toString(),
                                reasoningPreview.text,
                                isComplete = true,
                                finishReason = event.finishReason,
                                usage = event.usage,
                                reasoningCharacterCount = reasoning.length,
                                reasoningIsPreview = reasoningPreview.isPreview,
                            ),
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            if (!completed) withContext(NonCancellable) { persist(MessageGenerationStatus.CANCELLED) }
            throw cancelled
        } catch (failure: Exception) {
            if (!completed) withContext(NonCancellable) { persist(MessageGenerationStatus.FAILED) }
            throw failure
        }
    }

    private companion object {
        const val UI_UPDATE_INTERVAL_NANOS = 100_000_000L
        const val PERSIST_INTERVAL_NANOS = 300_000_000L
        const val LONG_REASONING_PERSIST_INTERVAL_NANOS = 2_000_000_000L
        const val LONG_REASONING_THRESHOLD = 32_000
        const val REASONING_PREVIEW_HEAD = 4_000
        const val REASONING_PREVIEW_TAIL = 8_000
    }

    private data class UiPreview(val text: String, val isPreview: Boolean)

    private fun StringBuilder.toUiPreview(): UiPreview {
        if (length <= REASONING_PREVIEW_HEAD + REASONING_PREVIEW_TAIL) return UiPreview(toString(), false)
        return UiPreview(
            text = substring(0, REASONING_PREVIEW_HEAD) + "\n\n…\n\n" +
                substring(length - REASONING_PREVIEW_TAIL, length),
            isPreview = true,
        )
    }
}
