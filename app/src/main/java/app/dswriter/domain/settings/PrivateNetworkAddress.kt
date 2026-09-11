package app.dswriter.domain.settings

/**
 * Recognizes addresses that can only be reached inside the user's own network.
 *
 * The check is deliberately text based. Resolving a hostname would require network access,
 * could be changed later by DNS, and would make the request path depend on the resolver.
 * A host therefore qualifies only when it is already written as a private, loopback,
 * or link-local address.
 */
object PrivateNetworkAddress {
    private val IPV4_SEGMENT = Regex("""\d{1,3}""")

    fun isPrivate(host: String?): Boolean {
        val value = host?.trim()?.removeSurrounding("[", "]")?.lowercase().orEmpty()
        if (value.isEmpty()) return false
        if (value == "localhost") return true
        if (value.contains(':')) return isPrivateIpv6(value)
        return isPrivateIpv4(value)
    }

    private fun isPrivateIpv4(value: String): Boolean {
        val segments = value.split('.')
        if (segments.size != 4) return false
        val octets = segments.map { segment ->
            if (!IPV4_SEGMENT.matches(segment)) return false
            val number = segment.toIntOrNull() ?: return false
            if (number > 255) return false
            number
        }
        return when {
            octets[0] == 10 -> true
            octets[0] == 127 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 169 && octets[1] == 254 -> true
            else -> false
        }
    }

    private fun isPrivateIpv6(value: String): Boolean {
        if (value == "::1") return true
        // Unique local addresses use fc00::/7, so the first byte is 0xfc or 0xfd.
        val firstGroup = value.substringBefore(':')
        if (firstGroup.length < 2) return false
        val firstByte = firstGroup.take(2).toIntOrNull(16) ?: return false
        if (firstByte and 0xfe == 0xfc) return true
        // Link-local unicast uses fe80::/10.
        return firstByte == 0xfe && firstGroup.getOrNull(2)?.let { it in '8'..'b' } == true
    }
}
