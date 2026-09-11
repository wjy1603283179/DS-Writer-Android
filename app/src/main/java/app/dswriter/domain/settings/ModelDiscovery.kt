package app.dswriter.domain.settings

/**
 * One model a server advertises at `GET /models`.
 *
 * The id is what must be sent as the request `model` field. Servers commonly set an alias
 * (`--alias novel-a`) and also list the underlying file path, so both are captured and the alias
 * is preferred as the value to send.
 */
data class DiscoveredModel(
    val id: String,
    val displayLabel: String,
)

/**
 * Optional connection check that lists the models a server offers.
 *
 * It exists because the alias is server-chosen and a user cannot be expected to guess it.
 * Asking the server is the only way to know what to send, and it keeps the app generic: any
 * OpenAI-compatible server works, and no vendor or model name is compiled in.
 */
interface ModelDiscovery {
    suspend fun listModels(baseUrl: String, apiKey: String?): ModelDiscoveryResult
}

sealed interface ModelDiscoveryResult {
    data class Success(val models: List<DiscoveredModel>) : ModelDiscoveryResult

    /** The server answered but the payload was not a usable model list. */
    data object Unsupported : ModelDiscoveryResult

    data class Failure(val reason: ModelDiscoveryFailure) : ModelDiscoveryResult
}

enum class ModelDiscoveryFailure {
    TIMEOUT,
    TLS,
    NETWORK,
    SERVER,
}
