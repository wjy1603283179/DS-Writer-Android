package app.dswriter.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiEndpointBuilderTest {
    @Test
    fun `joins an OpenAI-compatible v1 prefix`() {
        assertEquals(
            "http://192.168.1.10:8555/v1/models",
            ApiEndpointBuilder.modelsUrl("http://192.168.1.10:8555/v1"),
        )
        assertEquals(
            "http://192.168.1.10:8555/v1/chat/completions",
            ApiEndpointBuilder.chatCompletionsUrl("http://192.168.1.10:8555/v1"),
        )
    }

    @Test
    fun `joins a bare origin with an explicit port`() {
        assertEquals(
            "http://192.168.1.10:8555/models",
            ApiEndpointBuilder.modelsUrl("http://192.168.1.10:8555"),
        )
    }

    @Test
    fun `joins a bare origin without a port`() {
        assertEquals(
            "https://api.deepseek.com/models",
            ApiEndpointBuilder.modelsUrl("https://api.deepseek.com"),
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            ApiEndpointBuilder.chatCompletionsUrl("https://api.deepseek.com"),
        )
    }

    @Test
    fun `never doubles or drops a separating slash`() {
        assertEquals(
            "https://example.com/v1/models",
            ApiEndpointBuilder.modelsUrl("https://example.com/v1/"),
        )
        assertEquals(
            "https://example.com/v1/models",
            ApiEndpointBuilder.modelsUrl("https://example.com/v1"),
        )
        assertEquals(
            "https://example.com/a/b/models",
            ApiEndpointBuilder.modelsUrl("https://example.com/a/b/"),
        )
    }
}
