package app.dswriter.data.remote

sealed interface ConnectionTestResult {
    data object Success : ConnectionTestResult

    data class Failure(val reason: ConnectionFailure) : ConnectionTestResult
}

enum class ConnectionFailure {
    UNAUTHORIZED,
    RATE_LIMITED,
    TIMEOUT,
    TLS,
    NETWORK,
    SERVER,
    UNEXPECTED_RESPONSE,
}

interface ApiConnectionTester {
    /**
     * [apiKey] is nullable because a local server commonly needs no key at all. The request is
     * still worth sending in that case: whether the address answers is exactly what a connection
     * test is for, and a server that does require a key answers with a 401 that says so.
     */
    suspend fun test(baseUrl: String, apiKey: String?): ConnectionTestResult
}
