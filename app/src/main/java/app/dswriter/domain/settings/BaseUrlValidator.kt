package app.dswriter.domain.settings

import java.net.URI

/**
 * Why a base URL cannot be used.
 *
 * Settings surfaces these as Chinese messages, so each case stays distinguishable.
 */
enum class BaseUrlRejection {
    /** Nothing was entered. A fresh install has no endpoint until the user supplies one. */
    REQUIRED,
    MALFORMED,
    UNSUPPORTED_SCHEME,
    MISSING_HOST,
    CREDENTIALS_PRESENT,
    QUERY_OR_FRAGMENT_PRESENT,
    INSECURE_PUBLIC_HOST,
}

/**
 * Result of validating a base URL.
 *
 * [LocalNetwork] marks a plain HTTP endpoint, which is accepted only for private, loopback,
 * and link-local addresses so a model server on the user's own network can be reached.
 * [Secure] covers every HTTPS endpoint, including an HTTPS address inside the local network.
 * Public endpoints reached over plain HTTP stay rejected.
 */
sealed interface BaseUrlResolution {
    val normalizedUrl: String

    data class Secure(override val normalizedUrl: String) : BaseUrlResolution

    data class LocalNetwork(override val normalizedUrl: String) : BaseUrlResolution

    data class Rejected(val reason: BaseUrlRejection) : BaseUrlResolution {
        override val normalizedUrl: String = ""
    }

    val isAccepted: Boolean get() = this !is Rejected
}

object BaseUrlValidator {
    fun normalize(value: String): BaseUrlResolution {
        val trimmed = value.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return BaseUrlResolution.Rejected(BaseUrlRejection.MALFORMED)
        // A fresh install starts with an empty address, so "not filled in yet" is its own case
        // rather than a malformed URL.
        if (trimmed.isEmpty()) {
            return BaseUrlResolution.Rejected(BaseUrlRejection.REQUIRED)
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return BaseUrlResolution.Rejected(BaseUrlRejection.UNSUPPORTED_SCHEME)
        }
        if (uri.host.isNullOrBlank()) {
            return BaseUrlResolution.Rejected(BaseUrlRejection.MISSING_HOST)
        }
        if (uri.userInfo != null) {
            return BaseUrlResolution.Rejected(BaseUrlRejection.CREDENTIALS_PRESENT)
        }
        if (uri.query != null || uri.fragment != null) {
            return BaseUrlResolution.Rejected(BaseUrlRejection.QUERY_OR_FRAGMENT_PRESENT)
        }

        val host = uri.host.lowercase()
        val isLocalNetwork = PrivateNetworkAddress.isPrivate(host)
        if (scheme == "http" && !isLocalNetwork) {
            return BaseUrlResolution.Rejected(BaseUrlRejection.INSECURE_PUBLIC_HOST)
        }

        val normalized = buildString {
            append(scheme)
            append("://")
            if (host.contains(':')) {
                append('[')
                append(host)
                append(']')
            } else {
                append(host)
            }
            if (uri.port != -1) {
                append(':')
                append(uri.port)
            }
            append(uri.path.orEmpty().trimEnd('/'))
        }
        return if (scheme == "http") {
            BaseUrlResolution.LocalNetwork(normalized)
        } else {
            BaseUrlResolution.Secure(normalized)
        }
    }
}
