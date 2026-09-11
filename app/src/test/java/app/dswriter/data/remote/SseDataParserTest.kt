package app.dswriter.data.remote

import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class SseDataParserTest {
    @Test
    fun `parses comments blank lines and multiline data`() = runTest {
        val source = Buffer().writeUtf8(
            ": keep-alive\n" +
                "data: first\n" +
                "data: second\n\n" +
                "event: ignored\n" +
                "data: [DONE]\n\n",
        )
        val events = mutableListOf<String>()

        SseDataParser.parse(source) { events += it }

        assertEquals(listOf("first\nsecond", "[DONE]"), events)
    }
}
