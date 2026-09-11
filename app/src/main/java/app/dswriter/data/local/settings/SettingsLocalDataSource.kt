package app.dswriter.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.dswriter.domain.model.DEFAULT_CONTEXT_WINDOW
import app.dswriter.domain.model.ModelTuning
import app.dswriter.domain.model.Provider
import app.dswriter.domain.model.ProviderCatalog
import app.dswriter.security.EncryptedApiKey
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Settings that do not belong to any single provider. */
data class StoredPreferences(val suggestRecapWhenContextIsFull: Boolean = false)

interface SettingsLocalDataSource {
    val catalog: Flow<ProviderCatalog>

    val preferences: Flow<StoredPreferences>

    /** Applies [transform] to the current catalog and persists the result. */
    suspend fun updateCatalog(transform: (ProviderCatalog) -> ProviderCatalog)

    suspend fun readApiKey(providerId: String): EncryptedApiKey?

    suspend fun writeApiKey(providerId: String, encryptedApiKey: EncryptedApiKey)

    suspend fun clearApiKey(providerId: String)

    suspend fun updateSuggestRecapWhenContextIsFull(enabled: Boolean)
}

/**
 * Stores the provider list as one JSON document.
 *
 * A document is used rather than a preference key per field because the number of providers is
 * unbounded and a provider's fields belong together. API keys are stored inline as ciphertext
 * only, and only after the settings repository has encrypted them.
 */
@Singleton
class DataStoreSettingsLocalDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsLocalDataSource {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override val catalog: Flow<ProviderCatalog> = dataStore.data.map { preferences ->
        readCatalog(preferences).toDomain()
    }

    override val preferences: Flow<StoredPreferences> = dataStore.data.map { preferences ->
        StoredPreferences(suggestRecapWhenContextIsFull = preferences[SUGGEST_RECAP] ?: false)
    }

    override suspend fun updateCatalog(transform: (ProviderCatalog) -> ProviderCatalog) {
        dataStore.edit { preferences ->
            val current = readCatalog(preferences)
            val updated = transform(current.toDomain())
            val storedById = current.providers.associateBy { it.id }
            val merged = updated.providers.map { provider ->
                // Preserve the stored key: it is never part of the in-memory provider.
                val stored = storedById[provider.id]
                StoredProvider(
                    id = provider.id,
                    name = provider.name,
                    baseUrl = provider.baseUrl,
                    apiKey = stored?.apiKey,
                    modelId = provider.modelId,
                    contextWindow = provider.contextWindow,
                    tuning = provider.tuning,
                    supportsVision = provider.supportsVision,
                )
            }
            write(preferences, StoredCatalog(merged, updated.activeProviderId))
        }
    }

    override suspend fun readApiKey(providerId: String): EncryptedApiKey? =
        readCatalog(dataStore.data.first()).providers
            .firstOrNull { it.id == providerId }
            ?.apiKey
            ?.toEncrypted()

    override suspend fun writeApiKey(providerId: String, encryptedApiKey: EncryptedApiKey) {
        dataStore.edit { preferences ->
            val current = readCatalog(preferences)
            write(
                preferences,
                current.copy(
                    providers = current.providers.map { provider ->
                        if (provider.id == providerId) {
                            provider.copy(apiKey = StoredApiKey.from(encryptedApiKey))
                        } else {
                            provider
                        }
                    },
                ),
            )
        }
    }

    override suspend fun clearApiKey(providerId: String) {
        dataStore.edit { preferences ->
            val current = readCatalog(preferences)
            write(
                preferences,
                current.copy(
                    providers = current.providers.map { provider ->
                        if (provider.id == providerId) provider.copy(apiKey = null) else provider
                    },
                ),
            )
        }
    }

    override suspend fun updateSuggestRecapWhenContextIsFull(enabled: Boolean) {
        dataStore.edit { preferences ->
            if (enabled) {
                preferences[SUGGEST_RECAP] = true
            } else {
                preferences.remove(SUGGEST_RECAP)
            }
        }
    }

    private fun write(preferences: MutablePreferences, catalog: StoredCatalog) {
        preferences[CATALOG] = json.encodeToString(StoredCatalog.serializer(), catalog)
    }

    /**
     * Reads the stored catalog, converting the earlier single-provider layout on first read.
     *
     * The previous format kept one base URL, one key, and one model in separate preference keys.
     * Those values become the first provider so an upgrade keeps working; the legacy keys are then
     * ignored and are overwritten by the first catalog write.
     */
    private fun readCatalog(preferences: Preferences): StoredCatalog {
        preferences[CATALOG]?.let { encoded ->
            runCatching { json.decodeFromString(StoredCatalog.serializer(), encoded) }
                .getOrNull()
                ?.let { return it }
        }
        return migrateLegacy(preferences)
    }

    /**
     * Reads the legacy single-provider layout.
     *
     * This stays pure because it runs while mapping the stored preferences. The converted catalog
     * is persisted by the next catalog write, which every provider edit performs, so a stale
     * legacy value cannot outlive the first change.
     */
    private fun migrateLegacy(preferences: Preferences): StoredCatalog {
        val baseUrl = preferences[LEGACY_BASE_URL].orEmpty()
        val modelId = preferences[LEGACY_LOCAL_MODEL_ID].orEmpty()
        val ciphertext = preferences[LEGACY_API_KEY_CIPHERTEXT]
        val iv = preferences[LEGACY_API_KEY_IV]
        if (baseUrl.isBlank() && modelId.isBlank() && ciphertext == null) return StoredCatalog()
        val provider = StoredProvider(
            id = MIGRATED_PROVIDER_ID,
            name = MIGRATED_PROVIDER_NAME,
            baseUrl = baseUrl,
            modelId = modelId,
            contextWindow = preferences[LEGACY_CONTEXT_WINDOW] ?: DEFAULT_CONTEXT_WINDOW,
            apiKey = if (ciphertext != null && iv != null) StoredApiKey(ciphertext, iv) else null,
        )
        return StoredCatalog(providers = listOf(provider), activeProviderId = provider.id)
    }

    private companion object {
        val CATALOG = stringPreferencesKey("provider_catalog")
        val SUGGEST_RECAP = booleanPreferencesKey("suggest_recap_when_context_full")

        // Written by an earlier version. Read only for migration.
        val LEGACY_BASE_URL = stringPreferencesKey("base_url")
        val LEGACY_API_KEY_CIPHERTEXT = stringPreferencesKey("api_key_ciphertext")
        val LEGACY_API_KEY_IV = stringPreferencesKey("api_key_iv")
        val LEGACY_LOCAL_MODEL_ID = stringPreferencesKey("local_model_id")
        val LEGACY_CONTEXT_WINDOW = intPreferencesKey("local_context_window")

        const val MIGRATED_PROVIDER_ID = "migrated-default"
        const val MIGRATED_PROVIDER_NAME = "默认服务"
    }
}

@Serializable
internal data class StoredCatalog(
    val providers: List<StoredProvider> = emptyList(),
    @SerialName("activeProviderId") val activeProviderId: String? = null,
) {
    fun toDomain(): ProviderCatalog = ProviderCatalog(
        providers = providers.map { it.toDomain() },
        activeProviderId = activeProviderId,
    )
}

@Serializable
internal data class StoredProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: StoredApiKey? = null,
    val modelId: String = "",
    val contextWindow: Int = DEFAULT_CONTEXT_WINDOW,
    val tuning: ModelTuning = ModelTuning(),
    val supportsVision: Boolean = false,
) {
    fun toDomain() = Provider(
        id = id,
        name = name,
        baseUrl = baseUrl,
        hasApiKey = apiKey != null,
        modelId = modelId,
        contextWindow = contextWindow,
        tuning = tuning,
        supportsVision = supportsVision,
    )
}

@Serializable
internal data class StoredApiKey(val ciphertext: String, val iv: String) {
    fun toEncrypted() = EncryptedApiKey(
        ciphertext = ciphertext.decodeBase64() ?: ByteArray(0),
        initializationVector = iv.decodeBase64() ?: ByteArray(0),
    )

    companion object {
        fun from(encrypted: EncryptedApiKey) = StoredApiKey(
            ciphertext = Base64.getEncoder().encodeToString(encrypted.ciphertext),
            iv = Base64.getEncoder().encodeToString(encrypted.initializationVector),
        )
    }
}

private fun String.decodeBase64(): ByteArray? = runCatching {
    Base64.getDecoder().decode(this)
}.getOrNull()
