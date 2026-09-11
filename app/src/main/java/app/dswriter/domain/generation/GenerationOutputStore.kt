package app.dswriter.domain.generation

import app.dswriter.data.local.db.MessageGenerationStatus

interface GenerationOutputStore {
    suspend fun createStreamingAssistantMessage(
        conversationId: String,
        parentMessageId: String,
    ): String

    suspend fun persist(
        messageId: String,
        content: String,
        reasoningContent: String,
        status: MessageGenerationStatus,
    )
}

fun interface MonotonicTimeSource {
    fun nanoTime(): Long
}
