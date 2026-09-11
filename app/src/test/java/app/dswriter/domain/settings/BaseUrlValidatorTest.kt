package app.dswriter.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseUrlValidatorTest {
    @Test
    fun `normalizes HTTPS host and trailing slash`() {
        assertEquals(
            BaseUrlResolution.Secure("https://api.deepseek.com"),
            BaseUrlValidator.normalize("  HTTPS://API.DEEPSEEK.COM/  "),
        )
    }

    @Test
    fun `preserves a valid path prefix`() {
        assertEquals(
            BaseUrlResolution.Secure("https://example.com/deepseek/v1"),
            BaseUrlValidator.normalize("https://example.com/deepseek/v1/"),
        )
    }

    @Test
    fun `accepts plain HTTP for private network addresses`() {
        val cases = mapOf(
            "http://192.168.1.10:8555/v1" to "http://192.168.1.10:8555/v1",
            "http://192.168.1.10:8555/v1/" to "http://192.168.1.10:8555/v1",
            "http://10.0.0.5:8080/v1" to "http://10.0.0.5:8080/v1",
            "http://172.16.4.4/v1" to "http://172.16.4.4/v1",
            "http://127.0.0.1:8555/v1" to "http://127.0.0.1:8555/v1",
            "http://localhost:8555/v1" to "http://localhost:8555/v1",
        )
        cases.forEach { (input, expected) ->
            assertEquals(
                "expected $input to normalize to $expected",
                BaseUrlResolution.LocalNetwork(expected),
                BaseUrlValidator.normalize(input),
            )
        }
    }

    @Test
    fun `rejects plain HTTP for public hosts`() {
        val cases = listOf(
            "http://api.deepseek.com",
            "http://example.com/v1",
            "http://8.8.8.8/v1",
            "http://172.32.0.1/v1",
            "http://192.169.1.1/v1",
            "http://169.253.1.1/v1",
        )
        cases.forEach { input ->
            assertEquals(
                "expected $input to be rejected",
                BaseUrlResolution.Rejected(BaseUrlRejection.INSECURE_PUBLIC_HOST),
                BaseUrlValidator.normalize(input),
            )
        }
    }

    @Test
    fun `rejects insecure or ambiguous URLs`() {
        assertEquals(
            BaseUrlResolution.Rejected(BaseUrlRejection.CREDENTIALS_PRESENT),
            BaseUrlValidator.normalize("https://user@example.com"),
        )
        assertEquals(
            BaseUrlResolution.Rejected(BaseUrlRejection.QUERY_OR_FRAGMENT_PRESENT),
            BaseUrlValidator.normalize("https://example.com?key=value"),
        )
        assertEquals(
            BaseUrlResolution.Rejected(BaseUrlRejection.MALFORMED),
            BaseUrlValidator.normalize("not a URL"),
        )
        assertEquals(
            BaseUrlResolution.Rejected(BaseUrlRejection.UNSUPPORTED_SCHEME),
            BaseUrlValidator.normalize("ftp://example.com"),
        )
    }

    @Test
    fun `keeps HTTPS local addresses in the secure class`() {
        assertEquals(
            BaseUrlResolution.Secure("https://192.168.1.10:8443/v1"),
            BaseUrlValidator.normalize("https://192.168.1.10:8443/v1"),
        )
    }

    @Test
    fun `marks only accepted resolutions as accepted`() {
        assertTrue(BaseUrlResolution.Secure("https://api.deepseek.com").isAccepted)
        assertTrue(BaseUrlResolution.LocalNetwork("http://192.168.1.10/v1").isAccepted)
        assertFalse(BaseUrlResolution.Rejected(BaseUrlRejection.MALFORMED).isAccepted)
    }
}
