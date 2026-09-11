package app.dswriter.data.remote

import app.dswriter.domain.generation.GenerationRequestSnapshot
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import app.dswriter.data.local.attachment.ImageDataUrlEncoder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class OkHttpChatStreamingClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val imageEncoder: ImageDataUrlEncoder,
) : ChatStreamingClient {
    override fun stream(
        snapshot: GenerationRequestSnapshot,
        apiKey: String,
    ): Flow<GenerationStreamEvent> = callbackFlow {
        val body = json.encodeToString(ChatCompletionRequest.serializer(), snapshot.toApiRequest(imageEncoder))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(ApiEndpointBuilder.chatCompletionsUrl(snapshot.baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        val call = client.newCall(request)
        // Streaming may legitimately run for minutes. Keep the shared client's bounded
        // connection-test deadline, but remove the whole-call deadline for this cancellable SSE call.
        call.timeout().clearTimeout()

        val reader = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw response.toApiException(json)
                    val source = response.body?.source()
                        ?: throw ApiException(RemoteFailure.INVALID_RESPONSE)
                    var finishReason: FinishReason? = null
                    var usage: ApiUsage? = null
                    var done = false
                    SseDataParser.parse(source) { data ->
                        if (data == "[DONE]") {
                            if (!done) send(GenerationStreamEvent.Completed(finishReason, usage))
                            done = true
                        } else {
                            val chunk = try {
                                json.decodeFromString(ChatCompletionChunk.serializer(), data)
                            } catch (exception: Exception) {
                                throw ApiException(RemoteFailure.INVALID_RESPONSE, cause = exception)
                            }
                            chunk.choices.forEach { choice ->
                                choice.delta.reasoningContent?.takeIf(String::isNotEmpty)?.let {
                                    send(GenerationStreamEvent.ReasoningDelta(it))
                                }
                                choice.delta.content?.takeIf(String::isNotEmpty)?.let {
                                    send(GenerationStreamEvent.ContentDelta(it))
                                }
                                choice.finishReason?.let { finishReason = it.toFinishReason() }
                            }
                            chunk.usage?.let { usage = it }
                        }
                    }
                    if (!done) send(GenerationStreamEvent.Completed(finishReason, usage))
                    close()
                }
            } catch (exception: Exception) {
                close(exception.toStreamingException())
            }
        }

        awaitClose {
            call.cancel()
            reader.cancel()
        }
    }

    private fun String.toFinishReason(): FinishReason = when (this) {
        "stop" -> FinishReason.STOP
        "length" -> FinishReason.LENGTH
        "content_filter" -> FinishReason.CONTENT_FILTER
        "tool_calls" -> FinishReason.TOOL_CALLS
        "insufficient_system_resource" -> FinishReason.INSUFFICIENT_SYSTEM_RESOURCE
        else -> FinishReason.UNKNOWN
    }

    private fun okhttp3.Response.toApiException(json: Json): ApiException {
        val apiCode = runCatching {
            body?.string()?.let { json.decodeFromString(ApiErrorEnvelope.serializer(), it).error?.code }
        }.getOrNull()
        val failure = when (code) {
            401, 403 -> RemoteFailure.UNAUTHORIZED
            429 -> RemoteFailure.RATE_LIMITED
            in 500..599 -> RemoteFailure.SERVER
            else -> RemoteFailure.INVALID_RESPONSE
        }
        return ApiException(failure, code, apiCode)
    }

    private fun Exception.toStreamingException(): Exception = when (this) {
        is ApiException -> this
        is SocketTimeoutException -> ApiException(RemoteFailure.TIMEOUT, cause = this)
        is SSLException -> ApiException(RemoteFailure.TLS, cause = this)
        is IOException -> ApiException(RemoteFailure.NETWORK, cause = this)
        else -> this
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
