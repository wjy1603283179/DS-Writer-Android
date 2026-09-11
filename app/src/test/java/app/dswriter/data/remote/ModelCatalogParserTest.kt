package app.dswriter.data.remote

import app.dswriter.domain.settings.DiscoveredModel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reads the alias a llama cpp server advertises`() {
        val models = ModelCatalogParser.parse(
            """
            {"object":"list","data":[
              {"id":"novel-a","object":"model","owned_by":"llama.cpp"},
              {"id":"novel-b","object":"model","owned_by":"llama.cpp"}
            ]}
            """.trimIndent(),
            json,
        )

        assertEquals(
            listOf(DiscoveredModel("novel-a", "novel-a"), DiscoveredModel("novel-b", "novel-b")),
            models,
        )
    }

    @Test
    fun `shortens a file path id for display but keeps it as the value to send`() {
        val models = requireNotNull(
            ModelCatalogParser.parse(
                """{"data":[{"id":"/models/A/Qwen3.6-35B-A3B-Uncensored-Q4_K_M.gguf"}]}""",
                json,
            ),
        )

        assertEquals("/models/A/Qwen3.6-35B-A3B-Uncensored-Q4_K_M.gguf", models.single().id)
        assertEquals("Qwen3.6-35B-A3B-Uncensored-Q4_K_M", models.single().displayLabel)
    }

    @Test
    fun `prefers a declared name for display`() {
        val models = requireNotNull(
            ModelCatalogParser.parse(
                """{"data":[{"id":"novel-a","name":"Novel A - Qwen3.6-35B-A3B"}]}""",
                json,
            ),
        )

        assertEquals("novel-a", models.single().id)
        assertEquals("Novel A - Qwen3.6-35B-A3B", models.single().displayLabel)
    }

    @Test
    fun `falls back to the name field when id is absent`() {
        val models = requireNotNull(
            ModelCatalogParser.parse("""{"data":[{"name":"local-model"}]}""", json),
        )

        assertEquals("local-model", models.single().id)
    }

    @Test
    fun `ignores duplicates and unusable entries`() {
        val models = requireNotNull(
            ModelCatalogParser.parse(
                """{"data":[{"id":"novel-a"},{"id":"novel-a"},{"object":"model"},{"id":""},5]}""",
                json,
            ),
        )

        assertEquals(listOf("novel-a"), models.map { it.id })
    }

    @Test
    fun `reports unsupported payloads instead of guessing`() {
        assertNull(ModelCatalogParser.parse("""{"object":"list"}""", json))
        assertNull(ModelCatalogParser.parse("""{"data":"none"}""", json))
        assertNull(ModelCatalogParser.parse("not json at all", json))
    }

    @Test
    fun `accepts an empty model list`() {
        val models = requireNotNull(ModelCatalogParser.parse("""{"data":[]}""", json))

        assertTrue(models.isEmpty())
    }
}
