package app.dswriter.data.remote

import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Singleton
class OkHttpApiConnectionTester @Inject constructor(
    private val client: OkHttpClient,
) : ApiConnectionTester {
    override suspend fun test(baseUrl: String, apiKey: String?): ConnectionTestResult =
        suspendCancellableCoroutine { continuation ->
            val builder = Request.Builder()
                .url(ApiEndpointBuilder.modelsUrl(baseUrl))
                .get()
            // Same rule as model discovery: send the header only when there is a key to send, so a
            // keyless local server can still be tested instead of being reported as unauthorized.
            if (!apiKey.isNullOrBlank()) {
                builder.header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$apiKey")
            }
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resume(ConnectionTestResult.Failure(e.toFailure()))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (continuation.isActive) {
                                continuation.resume(it.code.toResult())
                            }
                        }
                    }
                },
            )
        }

    private fun Int.toResult(): ConnectionTestResult = when {
        this in 200..299 -> ConnectionTestResult.Success
        this == 401 || this == 403 -> ConnectionTestResult.Failure(ConnectionFailure.UNAUTHORIZED)
        this == 429 -> ConnectionTestResult.Failure(ConnectionFailure.RATE_LIMITED)
        this >= 500 -> ConnectionTestResult.Failure(ConnectionFailure.SERVER)
        else -> ConnectionTestResult.Failure(ConnectionFailure.UNEXPECTED_RESPONSE)
    }

    private fun IOException.toFailure(): ConnectionFailure = when (this) {
        is SocketTimeoutException -> ConnectionFailure.TIMEOUT
        is SSLException -> ConnectionFailure.TLS
        else -> ConnectionFailure.NETWORK
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
