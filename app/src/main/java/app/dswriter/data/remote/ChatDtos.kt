package app.dswriter.data.remote

import app.dswriter.domain.generation.GenerationRequestSnapshot
import app.dswriter.domain.generation.RequestRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import app.dswriter.data.local.attachment.ImageDataUrlEncoder

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    @SerialName("stream_options") val streamOptions: StreamOptions = StreamOptions(),
    /**
     * Sampling and length controls, mirroring how the reference harness builds this request
     * (`stream_options.include_usage`, `max_tokens`, and `temperature` when configured).
     * Each field is omitted when unset, so an official model's request body is unchanged.
     */
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
)

@Serializable
data class ApiMessage(
    val role: ApiRole,
    val content: JsonElement,
)

@Serializable
enum class ApiRole {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
}

@Serializable
data class StreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean = true,
)

@Serializable
data class ChatCompletionChunk(
    val choices: List<ChunkChoice> = emptyList(),
    val usage: ApiUsage? = null,
)

@Serializable
data class ChunkChoice(
    val delta: ChunkDelta = ChunkDelta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChunkDelta(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class ApiUsage(
    @SerialName("prompt_tokens") val promptTokens: Long,
    @SerialName("completion_tokens") val completionTokens: Long,
    @SerialName("total_tokens") val totalTokens: Long,
    @SerialName("prompt_cache_hit_tokens") val promptCacheHitTokens: Long? = null,
    @SerialName("prompt_cache_miss_tokens") val promptCacheMissTokens: Long? = null,
)

@Serializable
data class ApiErrorEnvelope(val error: ApiErrorBody? = null)

@Serializable
data class ApiErrorBody(val message: String? = null, val type: String? = null, val code: String? = null)

fun GenerationRequestSnapshot.toApiRequest(
    imageEncoder: ImageDataUrlEncoder? = null,
): ChatCompletionRequest = ChatCompletionRequest(
    model = modelId,
    maxTokens = tuning?.maxOutputTokens,
    temperature = tuning?.temperature,
    topP = tuning?.topP,
    messages = messages.map { message ->
        ApiMessage(
            role = if (message.role == RequestRole.USER) ApiRole.USER else ApiRole.ASSISTANT,
            content = if (message.attachments.isEmpty()) {
                JsonPrimitive(message.content)
            } else {
                require(message.role == RequestRole.USER)
                val encoder = requireNotNull(imageEncoder) { "An image encoder is required" }
                JsonArray(
                    listOf(
                        JsonObject(mapOf("type" to JsonPrimitive("text"), "text" to JsonPrimitive(message.content))),
                    ) + message.attachments.map { attachment ->
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("image_url"),
                                "image_url" to JsonObject(
                                    mapOf(
                                        "url" to JsonPrimitive(encoder.encode(attachment)),
                                        "detail" to JsonPrimitive("auto"),
                                    ),
                                ),
                            ),
                        )
                    },
                )
            },
        )
    },
)
