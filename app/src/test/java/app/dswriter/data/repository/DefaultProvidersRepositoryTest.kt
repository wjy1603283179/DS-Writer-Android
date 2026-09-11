package app.dswriter.data.repository

import app.dswriter.data.local.settings.SettingsLocalDataSource
import app.dswriter.data.local.settings.StoredPreferences
import app.dswriter.domain.model.ModelTuning
import app.dswriter.domain.model.Provider
import app.dswriter.domain.model.ProviderCatalog
import app.dswriter.domain.settings.ProviderDraft
import app.dswriter.security.ApiKeyCipher
import app.dswriter.security.EncryptedApiKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProvidersRepositoryTest {
    private val local = FakeSettingsLocalDataSource()
    private val repository = DefaultProvidersRepository(local, ReversingCipher())

    private fun draft(
        name: String = "本地",
        baseUrl: String = "http://192.168.1.10:8555/v1",
        modelId: String = "novel-a",
    ) = ProviderDraft(
        name = name,
        baseUrl = baseUrl,
        modelId = modelId,
        contextWindow = 24_576,
        tuning = ModelTuning(temperature = 0.8),
        supportsVision = false,
    )

    @Test
    fun `starts with no provider so the app contacts nothing`() = runTest {
        assertEquals(emptyList<Provider>(), repository.catalog.first().providers)
        assertNull(repository.catalog.first().active)
    }

    @Test
    fun `adds a provider and makes the first usable one active`() = runTest {
        val id = repository.addProvider(draft())

        val catalog = repository.catalog.first()
        assertEquals(1, catalog.providers.size)
        assertEquals(id, catalog.activeProviderId)
        val provider = catalog.providers.single()
        assertEquals("本地", provider.name)
        assertEquals("http://192.168.1.10:8555/v1", provider.baseUrl)
        assertEquals("novel-a", provider.modelId)
        assertTrue(provider.isConfigured)
    }

    @Test
    fun `a second provider does not steal the active selection`() = runTest {
        val first = repository.addProvider(draft(name = "A"))
        repository.addProvider(draft(name = "B", baseUrl = "https://b.test/v1", modelId = "m-b"))

        assertEquals(first, repository.catalog.first().activeProviderId)
        assertEquals(2, repository.catalog.first().providers.size)
    }

    @Test
    fun `switching the active provider is by stable id`() = runTest {
        val first = repository.addProvider(draft(name = "A"))
        val second = repository.addProvider(draft(name = "B", baseUrl = "https://b.test/v1", modelId = "m-b"))

        repository.setActiveProvider(second)

        assertEquals(second, repository.catalog.first().activeProviderId)
        assertEquals(first, repository.catalog.first().providers.first().id)
    }

    @Test
    fun `an unknown id is ignored when switching`() = runTest {
        val only = repository.addProvider(draft())

        repository.setActiveProvider("missing")

        assertEquals(only, repository.catalog.first().activeProviderId)
    }

    @Test
    fun `editing a provider keeps its id and its key`() = runTest {
        val id = repository.addProvider(draft())
        repository.updateApiKey(id, "secret-key")

        repository.upsertProvider(id, draft(name = "改名", modelId = "other-model"))

        val provider = repository.catalog.first().providers.single()
        assertEquals(id, provider.id)
        assertEquals("改名", provider.name)
        assertEquals("other-model", provider.modelId)
        assertTrue("renaming must not drop the stored key", provider.hasApiKey)

        // The stored key is still readable because the id did not change.
        local.storedKey(id)?.let { assertEquals("secret-key", local.decrypt(it)) }
    }

    @Test
    fun `removing the active provider falls back to a remaining one`() = runTest {
        val first = repository.addProvider(draft(name = "A"))
        val second = repository.addProvider(draft(name = "B", baseUrl = "https://b.test/v1", modelId = "m-b"))
        repository.setActiveProvider(second)

        repository.removeProvider(second)

        val catalog = repository.catalog.first()
        assertEquals(listOf(first), catalog.providers.map { it.id })
        assertEquals(first, catalog.activeProviderId)
    }

    @Test
    fun `removing the last provider clears the active selection`() = runTest {
        val id = repository.addProvider(draft())

        repository.removeProvider(id)

        assertTrue(repository.catalog.first().providers.isEmpty())
        assertNull(repository.catalog.first().activeProviderId)
    }

    @Test
    fun `a key is stored encrypted and can be cleared`() = runTest {
        val id = repository.addProvider(draft())

        repository.updateApiKey(id, "plain-secret")

        val stored = requireNotNull(local.storedKey(id))
        assertFalse(stored.ciphertext.contentEquals("plain-secret".toByteArray()))
        assertEquals("plain-secret", repository.getApiKey(id))

        repository.clearApiKey(id)

        assertNull(repository.getApiKey(id))
        assertFalse(repository.catalog.first().providers.single().hasApiKey)
    }

    @Test
    fun `a blank key clears rather than stores`() = runTest {
        val id = repository.addProvider(draft())
        repository.updateApiKey(id, "something")

        repository.updateApiKey(id, "   ")

        assertNull(repository.getApiKey(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unusable address is refused`() = runTest {
        repository.addProvider(draft(baseUrl = "not a url"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a public plain HTTP address is refused`() = runTest {
        repository.addProvider(draft(baseUrl = "http://example.com/v1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an out of range context window is refused`() = runTest {
        repository.addProvider(
            draft().copy(contextWindow = 10),
        )
    }

    @Test
    fun `the recap suggestion preference is off until switched on`() = runTest {
        assertFalse(repository.suggestRecapWhenContextIsFull.first())

        repository.updateSuggestRecapWhenContextIsFull(true)
        assertTrue(repository.suggestRecapWhenContextIsFull.first())
    }
}

private class FakeSettingsLocalDataSource : SettingsLocalDataSource {
    private val catalogState = MutableStateFlow(ProviderCatalog())
    private val preferenceState = MutableStateFlow(StoredPreferences())
    private val keys = mutableMapOf<String, EncryptedApiKey>()

    override val catalog: Flow<ProviderCatalog> = catalogState
    override val preferences: Flow<StoredPreferences> = preferenceState

    fun storedKey(providerId: String): EncryptedApiKey? = keys[providerId]

    fun decrypt(encrypted: EncryptedApiKey): String =
        String(encrypted.ciphertext.reversedArray(), Charsets.UTF_8)

    override suspend fun updateCatalog(transform: (ProviderCatalog) -> ProviderCatalog) {
        val updated = transform(catalogState.value)
        catalogState.value = updated.copy(
            providers = updated.providers.map { provider ->
                provider.copy(hasApiKey = keys.containsKey(provider.id))
            },
        )
    }

    override suspend fun readApiKey(providerId: String): EncryptedApiKey? = keys[providerId]

    override suspend fun writeApiKey(providerId: String, encryptedApiKey: EncryptedApiKey) {
        keys[providerId] = encryptedApiKey
        catalogState.value = catalogState.value.copy(
            providers = catalogState.value.providers.map {
                if (it.id == providerId) it.copy(hasApiKey = true) else it
            },
        )
    }

    override suspend fun clearApiKey(providerId: String) {
        keys.remove(providerId)
        catalogState.value = catalogState.value.copy(
            providers = catalogState.value.providers.map {
                if (it.id == providerId) it.copy(hasApiKey = false) else it
            },
        )
    }

    override suspend fun updateSuggestRecapWhenContextIsFull(enabled: Boolean) {
        preferenceState.value = StoredPreferences(suggestRecapWhenContextIsFull = enabled)
    }
}

private class ReversingCipher : ApiKeyCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedApiKey = EncryptedApiKey(
        ciphertext = plaintext.reversedArray(),
        initializationVector = byteArrayOf(1, 2, 3),
    )

    override fun decrypt(encryptedApiKey: EncryptedApiKey): ByteArray =
        encryptedApiKey.ciphertext.reversedArray()
}
