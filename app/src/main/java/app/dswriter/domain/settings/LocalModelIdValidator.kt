package app.dswriter.domain.settings

/**
 * Validates a local model alias such as `novel-a`.
 *
 * The value is sent as the request `model` field, so it stays within the characters that
 * OpenAI-compatible servers accept for an alias. It is routing metadata, never conversation
 * content, and it is never used to build a prompt.
 */
object LocalModelIdValidator {
    const val MAX_LENGTH = 64
    private val ALLOWED = Regex("""[A-Za-z0-9._-]+""")

    fun normalize(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
        if (!ALLOWED.matches(trimmed)) return null
        return trimmed
    }

    /** An empty value is valid and clears local model support. */
    fun normalizeOptional(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        return normalize(trimmed)
    }
}
