package app.dswriter.data.remote

import app.dswriter.domain.generation.GenerationRequestSnapshot
import app.dswriter.domain.generation.RequestMessageSnapshot
import app.dswriter.domain.generation.RequestRole
import app.dswriter.domain.generation.RequestAttachmentSnapshot
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OkHttpChatStreamingClientTest {
    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private lateinit var client: OkHttpChatStreamingClient
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        httpClient = OkHttpClient()
        client = OkHttpChatStreamingClient(httpClient, json, object : app.dswriter.data.local.attachment.ImageDataUrlEncoder {
            override fun encode(attachment: app.dswriter.domain.generation.RequestAttachmentSnapshot) =
                "data:image/jpeg;base64,dGVzdA=="
        })
    }

    @After fun tearDown() = server.shutdown()

    @Test
    fun `streams reasoning content finish reason and usage`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"思考\"},\"finish_reason\":null}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\"回答\"},\"finish_reason\":\"stop\"}]," +
                    "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}}\n\n" +
                    "data: [DONE]\n\n",
            ),
        )

        val events = client.stream(snapshot(), "secret-key").toList()

        assertEquals(GenerationStreamEvent.ReasoningDelta("思考"), events[0])
        assertEquals(GenerationStreamEvent.ContentDelta("回答"), events[1])
        val completed = events[2] as GenerationStreamEvent.Completed
        assertEquals(FinishReason.STOP, completed.finishReason)
        assertEquals(5L, completed.usage?.totalTokens)
        val request = server.takeRequest()
        assertEquals("/chat/completions", request.path)
        assertEquals("Bearer secret-key", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"content\":\"继续\""))
        assertFalse(body.contains("system"))
    }

    @Test
    fun `maps HTTP failures without returning response content`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":\"rate_limit\"}}"))

        val failure = runCatching { client.stream(snapshot(), "key").toList() }.exceptionOrNull()

        assertTrue(failure is ApiException)
        assertEquals(RemoteFailure.RATE_LIMITED, (failure as ApiException).failure)
        assertEquals("rate_limit", failure.apiCode)
    }

    @Test
    fun `production transport serializes explicit image without adding a prompt`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("data: [DONE]\n\n"),
        )
        val visionSnapshot = GenerationRequestSnapshot(
            server.url("/").toString().trimEnd('/'),
            "vision-model",
            listOf(
                RequestMessageSnapshot(
                    RequestRole.USER,
                    " 原样看图🙂\n",
                    listOf(
                        RequestAttachmentSnapshot(
                            "image", "file:/private/image.jpg", "image/jpeg", "图.jpg", 10, 20, 4,
                        ),
                    ),
                ),
            ),
        )

        client.stream(visionSnapshot, "key").toList()
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val message = body.getValue("messages").jsonArray.single().jsonObject
        val content = message.getValue("content").jsonArray

        assertEquals("user", message.getValue("role").jsonPrimitive.content)
        assertEquals(" 原样看图🙂\n", content[0].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(
            "data:image/jpeg;base64,dGVzdA==",
            content[1].jsonObject.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content,
        )
        assertFalse(body.toString().contains("system", ignoreCase = true))
        assertFalse(body.toString().contains("developer", ignoreCase = true))
    }

    @Test
    fun `collector cancellation cancels the underlying call`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch { client.stream(snapshot(), "key").collect() }
        val request = withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }
        assertTrue(request != null)

        job.cancelAndJoin()

        repeat(20) {
            if (httpClient.dispatcher.runningCallsCount() == 0) return@repeat
            Thread.sleep(10)
        }
        assertEquals(0, httpClient.dispatcher.runningCallsCount())
    }

    @Test
    fun `streaming clears the whole-call deadline for long generations`() = runBlocking {
        val shortDeadlineClient = OkHttpClient.Builder()
            .callTimeout(50, TimeUnit.MILLISECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
        val streamingClient = OkHttpChatStreamingClient(shortDeadlineClient, json, object : app.dswriter.data.local.attachment.ImageDataUrlEncoder {
            override fun encode(attachment: RequestAttachmentSnapshot) = "data:image/jpeg;base64,dGVzdA=="
        })
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBodyDelay(150, TimeUnit.MILLISECONDS)
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"完成\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"),
        )

        val events = streamingClient.stream(snapshot(), "key").toList()

        assertTrue(events.any { it == GenerationStreamEvent.ContentDelta("完成") })
        assertTrue(events.last() is GenerationStreamEvent.Completed)
    }

    @Test
    fun `addresses an OpenAI-compatible v1 prefix with the local model alias`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"回答\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"),
        )
        val localSnapshot = GenerationRequestSnapshot(
            server.url("/").toString().trimEnd('/') + "/v1",
            "novel-a",
            listOf(RequestMessageSnapshot(RequestRole.USER, "继续")),
        )

        val events = client.stream(localSnapshot, "local-key").toList()

        assertTrue(events.any { it == GenerationStreamEvent.ContentDelta("回答") })
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"model\":\"novel-a\""))
        assertTrue(body.contains("\"content\":\"继续\""))
        assertFalse(body.contains("system"))
    }

    private fun snapshot() = GenerationRequestSnapshot(
        server.url("/").toString().trimEnd('/'),
        "any-model",
        listOf(RequestMessageSnapshot(RequestRole.USER, "继续")),
    )
}
