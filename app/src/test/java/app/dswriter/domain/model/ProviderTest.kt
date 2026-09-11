package app.dswriter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The application ships no built-in model table, so what a model is comes entirely from the
 * provider the user configured. These tests pin that behaviour.
 */
class ProviderTest {
    @Test
    fun `a provider is usable only once it has an address and a model`() {
        assertFalse(Provider(id = "1", name = "a", baseUrl = "", modelId = "").isConfigured)
        assertFalse(
            Provider(id = "1", name = "a", baseUrl = "https://x.test", modelId = "").isConfigured,
        )
        assertTrue(
            Provider(id = "1", name = "a", baseUrl = "https://x.test", modelId = "m").isConfigured,
        )
    }

    @Test
    fun `the catalog resolves the active provider by id`() {
        val first = Provider(id = "a", name = "A", baseUrl = "https://a.test", modelId = "m-a")
        val second = Provider(id = "b", name = "B", baseUrl = "https://b.test", modelId = "m-b")
        val catalog = ProviderCatalog(listOf(first, second), activeProviderId = "b")

        assertEquals(second, catalog.active)
        assertEquals(null, catalog.copy(activeProviderId = "missing").active)
        assertEquals(null, ProviderCatalog().active)
    }

    @Test
    fun `a provider becomes a resolved model without any vendor knowledge`() {
        val provider = Provider(
            id = "1",
            name = "我的服务",
            baseUrl = "http://192.168.1.10:8080/v1",
            modelId = "任意模型名",
            contextWindow = 32_768,
            tuning = ModelTuning(maxOutputTokens = 1_024, temperature = 0.7, topP = 0.9),
            supportsVision = true,
        )

        val model = provider.toResolvedModel()

        assertEquals("任意模型名", model.id)
        assertEquals("我的服务", model.providerName)
        assertEquals(32_768, model.contextBudget.maxRequestTokens)
        assertEquals(1_024, model.contextBudget.reservedOutputTokens)
        assertTrue(model.supportsVision)
        // An unset thinking capability is honest: the app sends no thinking toggle.
        assertFalse(model.supportsThinking)
    }

    @Test
    fun `an unset output cap is not sent so the server keeps its default`() {
        val model = Provider(
            id = "1",
            name = "a",
            baseUrl = "https://x.test",
            modelId = "m",
            contextWindow = 24_576,
        ).toResolvedModel()

        // The selector still reserves room for a reply, but the request must not claim a cap the
        // user never configured.
        assertEquals(
            ResolvedModel.DEFAULT_RESERVED_OUTPUT_TOKENS,
            model.contextBudget.reservedOutputTokens,
        )
        assertNull(model.effectiveTuning.maxOutputTokens)
    }

    @Test
    fun `a configured output cap is sent unchanged`() {
        val model = Provider(
            id = "1",
            name = "a",
            baseUrl = "https://x.test",
            modelId = "m",
            contextWindow = 24_576,
            tuning = ModelTuning(maxOutputTokens = 1_024, temperature = 0.8, topP = 0.9),
        ).toResolvedModel()

        assertEquals(1_024, model.effectiveTuning.maxOutputTokens)
        assertEquals(0.8, model.effectiveTuning.temperature!!, 0.0001)
        assertEquals(0.9, model.effectiveTuning.topP!!, 0.0001)
        assertEquals(1_024, model.contextBudget.reservedOutputTokens)
    }

    @Test
    fun `the output cap can never exceed the window it is reserved from`() {
        val model = Provider(
            id = "1",
            name = "a",
            baseUrl = "https://x.test",
            modelId = "m",
            contextWindow = MIN_CONTEXT_WINDOW,
            tuning = ModelTuning(maxOutputTokens = MAX_CONTEXT_WINDOW),
        ).toResolvedModel()

        val budget = model.contextBudget
        assertTrue(budget.reservedOutputTokens < budget.maxRequestTokens)
        assertTrue(budget.inputTokenLimit > 0)
    }

    @Test
    fun `tuning that sets nothing stays empty so the request omits it`() {
        assertTrue(ModelTuning().isEmpty)
        assertFalse(ModelTuning(temperature = 0.8).isEmpty)
        assertFalse(ModelTuning(maxOutputTokens = 100).isEmpty)
        assertFalse(ModelTuning(topP = 0.9).isEmpty)
    }

    @Test
    fun `the effective tuning never invents a value`() {
        val model = Provider(
            id = "1",
            name = "a",
            baseUrl = "https://x.test",
            modelId = "m",
            contextWindow = 24_576,
            tuning = ModelTuning(temperature = 0.8),
        ).toResolvedModel()

        val tuning = model.effectiveTuning
        assertEquals(0.8, tuning.temperature!!, 0.0001)
        assertNull("temperature alone must not add an output cap", tuning.maxOutputTokens)
    }
}
