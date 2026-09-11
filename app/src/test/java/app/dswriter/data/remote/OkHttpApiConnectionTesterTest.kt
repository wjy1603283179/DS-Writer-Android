package app.dswriter.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpApiConnectionTesterTest {
    private lateinit var server: MockWebServer
    private lateinit var tester: OkHttpApiConnectionTester

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tester = OkHttpApiConnectionTester(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `uses models endpoint with bearer authentication and no body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"object\":\"list\",\"data\":[]}"))

        val result = tester.test(server.url("/").toString().trimEnd('/'), "example-key")

        assertSame(ConnectionTestResult.Success, result)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/models", request.path)
        assertEquals("Bearer example-key", request.getHeader("Authorization"))
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun `maps authentication throttling and server responses`() = runTest {
        val cases = listOf(
            401 to ConnectionFailure.UNAUTHORIZED,
            429 to ConnectionFailure.RATE_LIMITED,
            503 to ConnectionFailure.SERVER,
            418 to ConnectionFailure.UNEXPECTED_RESPONSE,
        )

        cases.forEach { (statusCode, expected) ->
            server.enqueue(MockResponse().setResponseCode(statusCode))
            val result = tester.test(server.url("/").toString().trimEnd('/'), "example-key")
            assertEquals(ConnectionTestResult.Failure(expected), result)
        }
    }

    @Test
    fun `maps socket timeout without exposing an exception`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val timeoutTester = OkHttpApiConnectionTester(
            OkHttpClient.Builder().readTimeout(100, TimeUnit.MILLISECONDS).build(),
        )

        val result = timeoutTester.test(server.url("/").toString().trimEnd('/'), "example-key")

        assertEquals(ConnectionTestResult.Failure(ConnectionFailure.TIMEOUT), result)
    }

    @Test
    fun `maps unreachable host as a network failure`() = runTest {
        val unavailableServer = MockWebServer()
        unavailableServer.start()
        val unavailableUrl = unavailableServer.url("/").toString().trimEnd('/')
        unavailableServer.shutdown()

        val result = tester.test(unavailableUrl, "example-key")

        assertEquals(ConnectionTestResult.Failure(ConnectionFailure.NETWORK), result)
    }

    /**
     * A local server commonly has no key. Sending no Authorization header is what lets such a
     * server answer, so a keyless service must be tested by asking it rather than refused here.
     */
    @Test
    fun `tests a keyless server by omitting the authorization header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"object\":\"list\",\"data\":[]}"))

        val result = tester.test(server.url("/").toString().trimEnd('/'), null)

        assertSame(ConnectionTestResult.Success, result)
        val request = server.takeRequest()
        assertEquals("/models", request.path)
        assertEquals(null, request.getHeader("Authorization"))
    }

    @Test
    fun `treats a blank key the same as no key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"object\":\"list\",\"data\":[]}"))

        tester.test(server.url("/").toString().trimEnd('/'), "   ")

        assertEquals(null, server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `still reports unauthorized when a server demands a key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = tester.test(server.url("/").toString().trimEnd('/'), null)

        assertEquals(ConnectionTestResult.Failure(ConnectionFailure.UNAUTHORIZED), result)
    }
}
