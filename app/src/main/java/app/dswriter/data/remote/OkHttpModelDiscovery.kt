package app.dswriter.data.remote

import app.dswriter.domain.settings.ModelDiscovery
import app.dswriter.domain.settings.ModelDiscoveryFailure
import app.dswriter.domain.settings.ModelDiscoveryResult
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Lists the models a server advertises.
 *
 * The key is optional here: a local llama.cpp server accepts any key and may reject a request
 * that sends none, but a missing key must not stop the user from seeing the model list. The
 * Authorization header is therefore attached only when a key exists.
 */
@Singleton
class OkHttpModelDiscovery @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : ModelDiscovery {
    override suspend fun listModels(baseUrl: String, apiKey: String?): ModelDiscoveryResult =
        suspendCancellableCoroutine { continuation ->
            val builder = Request.Builder()
                .url(ApiEndpointBuilder.modelsUrl(baseUrl))
                .get()
            if (!apiKey.isNullOrBlank()) {
                builder.header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$apiKey")
            }
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resume(ModelDiscoveryResult.Failure(e.toFailure()))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!continuation.isActive) return
                            val result = if (!it.isSuccessful) {
                                ModelDiscoveryResult.Failure(ModelDiscoveryFailure.SERVER)
                            } else {
                                val body = runCatching { it.body?.string() }.getOrNull()
                                val models = body?.let { text -> ModelCatalogParser.parse(text, json) }
                                if (models == null) {
                                    ModelDiscoveryResult.Unsupported
                                } else {
                                    ModelDiscoveryResult.Success(models)
                                }
                            }
                            continuation.resume(result)
                        }
                    }
                },
            )
        }

    private fun IOException.toFailure(): ModelDiscoveryFailure = when (this) {
        is SocketTimeoutException -> ModelDiscoveryFailure.TIMEOUT
        is SSLException -> ModelDiscoveryFailure.TLS
        else -> ModelDiscoveryFailure.NETWORK
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
