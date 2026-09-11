package app.dswriter.domain.model

import app.dswriter.domain.context.ContextSelectionBudget
import kotlinx.serialization.Serializable

/**
 * Optional per-request sampling and length controls.
 *
 * These are request parameters, never conversation content: they carry no message, instruction,
 * prompt, or user text. They are per provider because servers differ, and they are omitted
 * entirely when unset so a request body stays whatever the provider's own defaults expect.
 */
@Serializable
data class ModelTuning(
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
) {
    val isEmpty: Boolean
        get() = maxOutputTokens == null && temperature == null && topP == null
}

/** Default context window for a server whose size the user has not stated. */
const val DEFAULT_CONTEXT_WINDOW = 24_576

/** Below this a window cannot hold one useful exchange. */
const val MIN_CONTEXT_WINDOW = 4_096

/** Upper bound for the setting. */
const val MAX_CONTEXT_WINDOW = 1_048_576

/**
 * A model configuration resolved for use in a request.
 *
 * Nothing here is provider-specific: the application ships no built-in model list and no vendor
 * name. A model exists because the user configured a provider and either discovered or typed a
 * model id, which is what makes the client work with any OpenAI-compatible server.
 */
data class ResolvedModel(
    val id: String,
    /** Provider label, used for display grouping. */
    val providerName: String,
    val contextWindow: Int,
    val supportsVision: Boolean,
    val supportsThinking: Boolean,
    val tuning: ModelTuning,
) {
    val contextBudget: ContextSelectionBudget
        get() {
            val window = contextWindow.coerceIn(MIN_CONTEXT_WINDOW, MAX_CONTEXT_WINDOW)
            // Keep the output reservation strictly inside the window so the budget invariant
            // holds; if the user set an output cap, it is what gets reserved.
            val reserved = (tuning.maxOutputTokens ?: DEFAULT_RESERVED_OUTPUT_TOKENS)
                .coerceAtMost(window / 2)
            return ContextSelectionBudget(
                maxRequestTokens = window,
                reservedOutputTokens = reserved,
            )
        }

    /**
     * The tuning actually sent.
     *
     * Only values the user set are sent. When no output cap was configured, `max_tokens` is
     * omitted entirely even though the selector still reserves room for a reply internally: a
     * blank field means "use the server's default", and silently imposing this app's own cap could
     * cut a reply short on a server whose default is larger.
     */
    val effectiveTuning: ModelTuning
        get() = tuning

    companion object {
        const val DEFAULT_RESERVED_OUTPUT_TOKENS = 2_560
    }
}

/**
 * One configured endpoint.
 *
 * [id] is stable across renames so a stored API key stays attached to the right provider.
 */
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val hasApiKey: Boolean = false,
    /** Model id to request. Empty until the user discovers or types one. */
    val modelId: String = "",
    val contextWindow: Int = DEFAULT_CONTEXT_WINDOW,
    val tuning: ModelTuning = ModelTuning(),
    val supportsVision: Boolean = false,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && modelId.isNotBlank()
}

/** The whole provider list plus which one is in use. */
data class ProviderCatalog(
    val providers: List<Provider> = emptyList(),
    val activeProviderId: String? = null,
) {
    val active: Provider? get() = providers.firstOrNull { it.id == activeProviderId }
}

/**
 * The request-facing view of a provider.
 *
 * Thinking is not advertised as a capability because the app does not send a thinking toggle;
 * whether a server emits reasoning is the server's business.
 */
fun Provider.toResolvedModel(): ResolvedModel = ResolvedModel(
    id = modelId,
    providerName = name,
    contextWindow = contextWindow,
    supportsVision = supportsVision,
    supportsThinking = false,
    tuning = tuning,
)
