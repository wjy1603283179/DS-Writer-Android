package app.dswriter.domain.generation

import app.dswriter.data.remote.ApiRole
import app.dswriter.data.remote.toApiRequest
import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationRole
import app.dswriter.domain.model.ModelTuning
import app.dswriter.domain.model.Provider
import app.dswriter.domain.model.ResolvedModel
import app.dswriter.domain.model.toResolvedModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapPromptsTest {
    /**
     * Recap is the one feature that sends something other than a plain conversation turn, so its
     * request shape is asserted explicitly rather than assumed.
     */
    private val productionJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val provider = Provider(
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
    fun `a recap request never introduces a system or developer role`() {
        val request = recap().snapshot.toApiRequest()

        assertEquals(listOf(ApiRole.USER), request.messages.map { it.role })
        assertFalse(request.messages.any { it.role != ApiRole.USER })
    }

    @Test
    fun `the serialized recap body contains no hidden instruction role`() {
        val body = productionJson.encodeToString(
            app.dswriter.data.remote.ChatCompletionRequest.serializer(),
            recap().snapshot.toApiRequest(),
        )
        val document = Json.parseToJsonElement(body).jsonObject
        val roles = document.getValue("messages").jsonArray
            .map { it.jsonObject.getValue("role").jsonPrimitive.content }

        assertEquals(listOf("user"), roles)
        assertFalse(body.contains("\"role\":\"system\""))
        assertFalse(body.contains("\"role\":\"developer\""))
    }

    @Test
    fun `the recap instruction is visible text inside the single real user message`() {
        val content = recap().snapshot.messages.single().content

        assertTrue(content.startsWith(RecapPrompts.SUMMARY_INSTRUCTION))
        // The material being condensed is the real text, unmodified.
        assertTrue(content.contains("第一章：雨夜"))
        assertTrue(content.contains("林砚之走进书店。"))
    }

    @Test
    fun `original message text is preserved inside the recap material`() {
        val exact = "他说：「继续。」"
        val snapshot = RecapPrompts.buildRecapRequest(
            provider,
            listOf(message("1", ConversationRole.USER, exact)),
        ).snapshot

        assertTrue(snapshot.messages.single().content.endsWith(exact))
    }

    @Test
    fun `a recap uses the configured provider and its tuning`() {
        val snapshot = RecapPrompts.buildRecapRequest(
            tunedProvider,
            listOf(message("1", ConversationRole.USER, "第一章")),
        ).snapshot

        assertEquals("novel-a", snapshot.modelId)
        assertEquals(tunedProvider.baseUrl, snapshot.baseUrl)
        assertEquals(0.8, snapshot.tuning?.temperature ?: 0.0, 0.0001)
        assertTrue(requireNotNull(snapshot.tuning?.maxOutputTokens) > 0)
    }

    @Test
    fun `a recap condenses the oldest text, not the newest`() {
        // The oldest material is the part a long conversation is about to lose, so that is what a
        // recap must record. Taking the newest messages would summarize what is still in context
        // and leave the beginning unrecorded.
        val content = RecapPrompts.buildRecapRequest(provider, longHistory()).snapshot
            .messages.single().content

        assertTrue("the earliest message must be condensed", content.contains("第 1 段："))
        assertFalse("the newest message must stay out of a recap", content.contains("第 40 段："))
    }

    @Test
    fun `a recap reports how much it could not fit`() {
        val request = RecapPrompts.buildRecapRequest(provider, longHistory())

        assertTrue(request.condensedMessageCount > 0)
        assertTrue("some material must remain for a second pass", request.remainingMessageCount > 0)
        assertEquals(40, request.condensedMessageCount + request.remainingMessageCount)
    }

    @Test
    fun `a short conversation is condensed in full`() {
        val request = recap()

        assertEquals(2, request.condensedMessageCount)
        assertEquals(0, request.remainingMessageCount)
    }

    @Test
    fun `oldestThatFit always returns at least one message`() {
        val huge = listOf(message("1", ConversationRole.USER, "内".repeat(400_000)))

        val selected = RecapPrompts.oldestThatFit(huge, provider.toResolvedModel())

        assertEquals(1, selected.size)
        assertEquals("1", selected.single().id)
    }

    @Test
    fun `the condensing budget follows the configured window`() {
        val narrow = provider.copy(
            contextWindow = ResolvedModel.DEFAULT_RESERVED_OUTPUT_TOKENS * 2,
        )
        val model = narrow.toResolvedModel()

        assertEquals(narrow.contextWindow, model.contextBudget.maxRequestTokens)
        assertTrue(model.contextBudget.reservedOutputTokens < model.contextBudget.maxRequestTokens)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a recap needs real material`() {
        RecapPrompts.buildRecapRequest(provider, emptyList())
    }

    private fun longHistory() = (1..40).map { index ->
        message(
            id = index.toString(),
            role = if (index % 2 == 1) ConversationRole.USER else ConversationRole.ASSISTANT,
            content = "第 $index 段：" + "内容".repeat(3000),
        )
    }

    private fun recap() = RecapPrompts.buildRecapRequest(
        provider,
        listOf(
            message("1", ConversationRole.USER, "第一章：雨夜"),
            message("2", ConversationRole.ASSISTANT, "林砚之走进书店。"),
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
