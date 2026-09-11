package app.dswriter.domain.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateNetworkAddressTest {
    @Test
    fun `recognizes private IPv4 ranges`() {
        listOf(
            "10.0.0.1",
            "10.255.255.254",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.0.1",
            "192.168.1.10",
            "127.0.0.1",
            "169.254.12.46",
            "localhost",
            "LOCALHOST",
        ).forEach { assertTrue("$it should be private", PrivateNetworkAddress.isPrivate(it)) }
    }

    @Test
    fun `rejects public and malformed IPv4 addresses`() {
        listOf(
            "8.8.8.8",
            "1.1.1.1",
            "172.15.0.1",
            "172.32.0.1",
            "192.169.0.1",
            "11.0.0.1",
            "169.253.0.1",
            "256.0.0.1",
            "192.168.1",
            "192.168.1.1.1",
            "192.168.1.a",
            "",
            "example.com",
        ).forEach { assertFalse("$it should not be private", PrivateNetworkAddress.isPrivate(it)) }
    }

    @Test
    fun `recognizes loopback and unique local IPv6 addresses`() {
        listOf("[::1]", "::1", "fc00::1", "fd12:3456::1", "fe80::1", "FE80::ABCD").forEach {
            assertTrue("$it should be private", PrivateNetworkAddress.isPrivate(it))
        }
    }

    @Test
    fun `rejects public IPv6 addresses`() {
        listOf("2001:4860:4860::8888", "2606:4700::1111", "ff02::1").forEach {
            assertFalse("$it should not be private", PrivateNetworkAddress.isPrivate(it))
        }
    }

    @Test
    fun `treats a null host as public`() {
        assertFalse(PrivateNetworkAddress.isPrivate(null))
    }
}
