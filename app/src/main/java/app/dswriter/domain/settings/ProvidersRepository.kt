package app.dswriter.domain.settings

import app.dswriter.domain.model.Provider
import app.dswriter.domain.model.ProviderCatalog
import kotlinx.coroutines.flow.Flow

/** Share of the input budget at which the optional recap suggestion appears. */
const val CONTEXT_PRESSURE_THRESHOLD = 0.8f

/**
 * Provider configuration: which endpoints exist, which one is in use, and their credentials.
 *
 * The application ships no built-in endpoint and no built-in model, so this repository is the only
 * source of where a request goes. A provider is identified by a stable id that survives renames,
 * so a stored API key stays attached to the correct entry.
 */
interface ProvidersRepository {
    val catalog: Flow<ProviderCatalog>

    /** Preferences that are not tied to a provider. */
    val suggestRecapWhenContextIsFull: Flow<Boolean>

    /** Creates a provider from [draft] and returns its new id. */
    suspend fun addProvider(draft: ProviderDraft): String

    /** Updates an existing provider, or creates it when [id] is unknown. */
    suspend fun upsertProvider(id: String?, draft: ProviderDraft)

    /** Removes a provider and its stored key. Clears the active selection if it pointed here. */
    suspend fun removeProvider(id: String)

    suspend fun setActiveProvider(id: String)

    /** Stores an encrypted key, or clears it when [apiKey] is blank. */
    suspend fun updateApiKey(providerId: String, apiKey: String)

    suspend fun clearApiKey(providerId: String)

    suspend fun getApiKey(providerId: String): String?

    suspend fun updateSuggestRecapWhenContextIsFull(enabled: Boolean)
}

/**
 * Validated input for creating or editing a provider.
 *
 * A blank [baseUrl] or [modelId] is permitted while the user is still filling the form; the UI
 * decides whether a provider is usable through [Provider.isConfigured].
 */
data class ProviderDraft(
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val contextWindow: Int,
    val tuning: app.dswriter.domain.model.ModelTuning,
    val supportsVision: Boolean,
)
