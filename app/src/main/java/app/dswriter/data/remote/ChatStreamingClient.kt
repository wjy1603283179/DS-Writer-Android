package app.dswriter.data.remote

import app.dswriter.domain.generation.GenerationRequestSnapshot
import kotlinx.coroutines.flow.Flow

sealed interface GenerationStreamEvent {
    data class ContentDelta(val text: String) : GenerationStreamEvent
    data class ReasoningDelta(val text: String) : GenerationStreamEvent
    data class Completed(val finishReason: FinishReason?, val usage: ApiUsage?) : GenerationStreamEvent
}

enum class FinishReason {
    STOP,
    LENGTH,
    CONTENT_FILTER,
    TOOL_CALLS,
    INSUFFICIENT_SYSTEM_RESOURCE,
    UNKNOWN,
}

enum class RemoteFailure {
    UNAUTHORIZED,
    RATE_LIMITED,
    TIMEOUT,
    TLS,
    NETWORK,
    SERVER,
    INVALID_RESPONSE,
}

class ApiException(
    val failure: RemoteFailure,
    val statusCode: Int? = null,
    val apiCode: String? = null,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

interface ChatStreamingClient {
    fun stream(snapshot: GenerationRequestSnapshot, apiKey: String): Flow<GenerationStreamEvent>
}
