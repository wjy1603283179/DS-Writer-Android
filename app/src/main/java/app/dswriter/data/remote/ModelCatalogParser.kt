package app.dswriter.data.remote

import app.dswriter.domain.settings.DiscoveredModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses an OpenAI-compatible `GET /models` payload.
 *
 * Only the `data` array of objects with a usable id or name is read; anything else is reported as
 * unsupported rather than guessed at. Servers differ in how they label a model, so a file path is
 * shortened to its file name for display while the raw value is kept as the id to send.
 */
object ModelCatalogParser {
    fun parse(body: String, json: Json): List<DiscoveredModel>? {
        val document = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return null
        val data = document["data"] as? JsonArray ?: return null

        val seen = mutableSetOf<String>()
        val models = mutableListOf<DiscoveredModel>()
        data.forEach { element ->
            val entry = element as? JsonObject ?: return@forEach
            val identifier = entry.primitive("id")?.takeIf { it.isNotBlank() }
                ?: entry.primitive("name")?.takeIf { it.isNotBlank() }
                ?: return@forEach
            if (!seen.add(identifier)) return@forEach
            val declaredName = entry.primitive("name")?.takeIf { it.isNotBlank() }
            models += DiscoveredModel(
                id = identifier,
                displayLabel = displayLabel(identifier, declaredName),
            )
        }
        return models
    }

    private fun JsonObject.primitive(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * A server may publish a filesystem path as the id; showing just the file name keeps the
     * picker readable without changing what is sent.
     */
    private fun displayLabel(identifier: String, declaredName: String?): String {
        if (!declaredName.isNullOrBlank()) return declaredName
        if (identifier.contains('/') || identifier.contains('\\')) {
            val fileName = identifier.substringAfterLast('/').substringAfterLast('\\')
            return fileName.removeSuffix(".gguf").ifBlank { identifier }
        }
        return identifier
    }
}
