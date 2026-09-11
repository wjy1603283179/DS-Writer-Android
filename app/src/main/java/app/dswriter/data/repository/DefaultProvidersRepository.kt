package app.dswriter.data.repository

import app.dswriter.data.local.settings.SettingsLocalDataSource
import app.dswriter.domain.model.MAX_CONTEXT_WINDOW
import app.dswriter.domain.model.MIN_CONTEXT_WINDOW
import app.dswriter.domain.model.Provider
import app.dswriter.domain.model.ProviderCatalog
import app.dswriter.domain.settings.BaseUrlResolution
import app.dswriter.domain.settings.BaseUrlValidator
import app.dswriter.domain.settings.ProviderDraft
import app.dswriter.domain.settings.ProvidersRepository
import app.dswriter.security.ApiKeyCipher
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DefaultProvidersRepository @Inject constructor(
    private val localDataSource: SettingsLocalDataSource,
    private val apiKeyCipher: ApiKeyCipher,
) : ProvidersRepository {
    override val catalog: Flow<ProviderCatalog> = localDataSource.catalog

    override val suggestRecapWhenContextIsFull: Flow<Boolean> =
        localDataSource.preferences.map { it.suggestRecapWhenContextIsFull }

    override suspend fun addProvider(draft: ProviderDraft): String {
        val id = UUID.randomUUID().toString()
        upsertProvider(id, draft)
        localDataSource.updateCatalog { current ->
            if (current.activeProviderId != null) {
                current
            } else {
                // The first usable provider becomes active so a new user can send immediately.
                current.copy(
                    activeProviderId = current.providers
                        .firstOrNull { it.id == id && it.isConfigured }
                        ?.id,
                )
            }
        }
        return id
    }

    override suspend fun upsertProvider(id: String?, draft: ProviderDraft) {
        val targetId = id ?: UUID.randomUUID().toString()
        val normalizedUrl = normalizeUrl(draft.baseUrl)
        require(normalizedUrl.isNotBlank()) { "A valid base URL is required" }
        require(draft.contextWindow in MIN_CONTEXT_WINDOW..MAX_CONTEXT_WINDOW) {
            "The context window is out of range"
        }
        localDataSource.updateCatalog { current ->
            val existing = current.providers.firstOrNull { it.id == targetId }
            val updated = Provider(
                id = targetId,
                name = draft.name.trim().ifBlank { DEFAULT_NAME },
                baseUrl = normalizedUrl,
                hasApiKey = existing?.hasApiKey ?: false,
                modelId = draft.modelId.trim(),
                contextWindow = draft.contextWindow,
                tuning = draft.tuning,
                supportsVision = draft.supportsVision,
            )
            ProviderCatalog(
                providers = if (existing == null) {
                    current.providers + updated
                } else {
                    current.providers.map { if (it.id == targetId) updated else it }
                },
                activeProviderId = current.activeProviderId,
            )
        }
    }

    override suspend fun removeProvider(id: String) {
        localDataSource.updateCatalog { current ->
            val remaining = current.providers.filterNot { it.id == id }
            ProviderCatalog(
                providers = remaining,
                activeProviderId = if (current.activeProviderId == id) {
                    remaining.firstOrNull()?.id
                } else {
                    current.activeProviderId
                },
            )
        }
    }

    override suspend fun setActiveProvider(id: String) {
        localDataSource.updateCatalog { current ->
            if (current.providers.none { it.id == id }) current
            else current.copy(activeProviderId = id)
        }
    }

    override suspend fun updateApiKey(providerId: String, apiKey: String) {
        if (apiKey.isBlank()) {
            clearApiKey(providerId)
            return
        }
        val plaintext = apiKey.toByteArray(StandardCharsets.UTF_8)
        try {
            localDataSource.writeApiKey(providerId, apiKeyCipher.encrypt(plaintext))
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun clearApiKey(providerId: String) {
        localDataSource.clearApiKey(providerId)
    }

    override suspend fun getApiKey(providerId: String): String? {
        val encrypted = localDataSource.readApiKey(providerId) ?: return null
        val plaintext = apiKeyCipher.decrypt(encrypted)
        return try {
            String(plaintext, StandardCharsets.UTF_8).takeIf { it.isNotEmpty() }
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun updateSuggestRecapWhenContextIsFull(enabled: Boolean) {
        localDataSource.updateSuggestRecapWhenContextIsFull(enabled)
    }

    private fun normalizeUrl(value: String): String {
        if (value.isBlank()) return ""
        return when (val resolution = BaseUrlValidator.normalize(value)) {
            is BaseUrlResolution.Secure -> resolution.normalizedUrl
            is BaseUrlResolution.LocalNetwork -> resolution.normalizedUrl
            is BaseUrlResolution.Rejected -> ""
        }
    }

    private companion object {
        const val DEFAULT_NAME = "新服务"
    }
}
