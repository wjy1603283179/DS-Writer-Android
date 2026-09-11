package app.dswriter.data.remote

/**
 * Builds endpoint URLs from a stored base URL.
 *
 * A base URL may carry a path prefix such as `/v1`, which is how OpenAI-compatible servers
 * including llama.cpp are addressed. Joining is done here so every request path agrees on
 * exactly one separating slash instead of relying on string interpolation at each call site.
 */
object ApiEndpointBuilder {
    fun modelsUrl(baseUrl: String): String = join(baseUrl, "models")

    fun chatCompletionsUrl(baseUrl: String): String = join(baseUrl, "chat/completions")

    private fun join(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}
