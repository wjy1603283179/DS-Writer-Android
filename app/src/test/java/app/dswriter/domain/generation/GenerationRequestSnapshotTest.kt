package app.dswriter.domain.generation

import app.dswriter.data.remote.ApiRole
import app.dswriter.data.remote.toApiRequest
import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationRole
import app.dswriter.domain.model.ConversationAttachment
import app.dswriter.domain.model.AttachmentState
import app.dswriter.domain.model.ModelTuning
import app.dswriter.domain.model.Provider
import app.dswriter.data.local.attachment.ImageDataUrlEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationRequestSnapshotTest {
    /**
     * Mirrors the production Json configuration in `AppModule.provideJson`. Default-valued fields
     * are part of the real request body, so tests must encode them too.
     */
    private val PRODUCTION_JSON = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val plainProvider = Provider(
        id = "p",
        name = "服务",
        baseUrl = "https://provider.test/v1",
        modelId = "any-model",
    )

    private val tunedProvider = Provider(
        id = "q",
        name = "调参服务",
        baseUrl = "http://192.168.1.10:8080/v1",
        modelId = "novel-a",
        contextWindow = 24_576,
        tuning = ModelTuning(maxOutputTokens = 2_560, temperature = 0.8, topP = 0.95),
    )

    @Test
    fun `maps only exact real conversation messages`() {
        val source = listOf(
            message("1", ConversationRole.USER, "你好"),
            message("2", ConversationRole.ASSISTANT, "你好，请问有什么可以帮你？"),
            message("3", ConversationRole.USER, "继续"),
        )

        val request = GenerationRequestSnapshotFactory.create(plainProvider, source).toApiRequest()

        assertEquals(listOf(ApiRole.USER, ApiRole.ASSISTANT, ApiRole.USER), request.messages.map { it.role })
        assertEquals(source.map { it.content }, request.messages.map { it.content.jsonPrimitive.content })
        assertEquals(3, request.messages.size)
    }

    @Test
    fun `serialized request has no hidden role or content`() {
        val snapshot = GenerationRequestSnapshotFactory.create(
            plainProvider,
            listOf(message("1", ConversationRole.USER, "继续")),
        )
        val document = Json.encodeToJsonElement(
            app.dswriter.data.remote.ChatCompletionRequest.serializer(),
            snapshot.toApiRequest(),
        ).jsonObject
        val messages = document.getValue("messages").jsonArray

        assertEquals(1, messages.size)
        assertEquals("user", messages.single().jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("继续", messages.single().jsonObject.getValue("content").jsonPrimitive.content)
        assertFalse(document.toString().contains("system"))
        assertFalse(document.toString().contains("developer"))
        assertFalse(document.toString().contains("项目"))
        assertFalse(document.toString().contains("继续写作"))
    }

    @Test
    fun `the request addresses whatever provider was configured`() {
        val snapshot = GenerationRequestSnapshotFactory.create(
            plainProvider,
            listOf(message("1", ConversationRole.USER, "继续")),
        )

        // Nothing about any vendor is compiled in: the model id is exactly the configured value.
        assertEquals("any-model", snapshot.toApiRequest().model)
        assertEquals("https://provider.test/v1", snapshot.baseUrl)
    }

    @Test
    fun `reasoning and interface metadata are never serialized as messages`() {
        val assistant = ConversationMessage(
            id = "1",
            conversationId = "对话标题",
            parentMessageId = null,
            role = ConversationRole.ASSISTANT,
            content = "最终回答",
            reasoningContent = "模型推理不得回灌",
            sequence = 1,
            createdAt = 1,
        )
        val currentUser = ConversationMessage(
            id = "2",
            conversationId = "对话标题",
            parentMessageId = "1",
            role = ConversationRole.USER,
            content = "继续",
            sequence = 2,
            createdAt = 2,
        )
        val body = Json.encodeToString(
            app.dswriter.data.remote.ChatCompletionRequest.serializer(),
            GenerationRequestSnapshotFactory.create(
                plainProvider,
                listOf(assistant, currentUser),
            ).toApiRequest(),
        )

        assertFalse(body.contains("模型推理不得回灌"))
        assertFalse(body.contains("对话标题"))
        assertTrue(body.contains("最终回答"))
        assertTrue(body.contains("继续"))
    }

    @Test
    fun `a configured tuning is sent and an unset one is omitted`() {
        val tuned = Json.parseToJsonElement(
            PRODUCTION_JSON.encodeToString(
                app.dswriter.data.remote.ChatCompletionRequest.serializer(),
                GenerationRequestSnapshotFactory.create(
                    tunedProvider,
                    listOf(message("1", ConversationRole.USER, "写一段")),
                ).toApiRequest(),
            ),
        ).jsonObject

        assertEquals(0.8, tuned.getValue("temperature").jsonPrimitive.double, 0.0001)
        assertEquals(0.95, tuned.getValue("top_p").jsonPrimitive.double, 0.0001)
        assertTrue(tuned.containsKey("max_tokens"))
        assertTrue(
            tuned.getValue("stream_options").jsonObject
                .getValue("include_usage").jsonPrimitive.boolean,
        )

        val plain = Json.parseToJsonElement(
            PRODUCTION_JSON.encodeToString(
                app.dswriter.data.remote.ChatCompletionRequest.serializer(),
                GenerationRequestSnapshotFactory.create(
                    plainProvider,
                    listOf(message("1", ConversationRole.USER, "继续")),
                ).toApiRequest(),
            ),
        ).jsonObject

        assertFalse("temperature must be omitted", plain.containsKey("temperature"))
        assertFalse("top_p must be omitted", plain.containsKey("top_p"))
        assertFalse("max_tokens must be omitted", plain.containsKey("max_tokens"))
    }

    @Test
    fun `the output cap never exceeds the server context window`() {
        val snapshot = GenerationRequestSnapshotFactory.create(
            tunedProvider,
            listOf(message("1", ConversationRole.USER, "继续")),
        )
        val reserved = requireNotNull(snapshot.tuning?.maxOutputTokens)
        assertTrue(reserved < tunedProvider.contextWindow)
        assertTrue(snapshot.estimatedInputTokens <= tunedProvider.contextWindow - reserved)
    }

    @Test
    fun `a provider without an address or model cannot build a request`() {
        val blank = Provider(id = "x", name = "空", baseUrl = "", modelId = "")
        val failure = runCatching {
            GenerationRequestSnapshotFactory.create(
                blank,
                listOf(message("1", ConversationRole.USER, "继续")),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test(expected = VisionModelRequiredException::class)
    fun `an image needs a provider that declares vision`() {
        GenerationRequestSnapshotFactory.create(plainProvider, listOf(messageWithImage()))
    }

    @Test
    fun `a vision provider accepts an image and maps it explicitly`() {
        val request = GenerationRequestSnapshotFactory.create(
            plainProvider.copy(supportsVision = true),
            listOf(messageWithImage()),
        ).toApiRequest(object : ImageDataUrlEncoder {
            override fun encode(attachment: RequestAttachmentSnapshot) =
                "data:image/jpeg;base64,dGVzdA=="
        })

        val content = request.messages.single().content.jsonArray
        assertEquals("text", content[0].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("image_url", content[1].jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test(expected = AttachmentUnavailableException::class)
    fun `restored image metadata without binary fails before network`() {
        val source = message("1", ConversationRole.USER, "继续").copy(
            attachments = listOf(
                ConversationAttachment(
                    "image", "1", "", "image/jpeg", "图.jpg",
                    100, 100, 10, AttachmentState.FAILED,
                ),
            ),
        )
        GenerationRequestSnapshotFactory.create(plainProvider.copy(supportsVision = true), listOf(source))
    }

    private fun messageWithImage() = message("1", ConversationRole.USER, "继续").copy(
        attachments = listOf(
            ConversationAttachment(
                "image", "1", "file:///private/a.jpg", "image/jpeg", "图.jpg",
                100, 100, 10, AttachmentState.READY,
            ),
        ),
    )

    private fun message(id: String, role: ConversationRole, content: String) = ConversationMessage(
        id = id,
        conversationId = "conversation",
        parentMessageId = null,
        role = role,
        content = content,
        sequence = id.toLong(),
        createdAt = id.toLong(),
    )
}
