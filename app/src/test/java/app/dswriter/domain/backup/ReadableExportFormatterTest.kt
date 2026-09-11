package app.dswriter.domain.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableExportFormatterTest {
    @Test
    fun `readable exports preserve exact Unicode message bodies and exclude sensitive metadata`() {
        val document = ExportDocument(
            conversations = listOf(ExportConversation("c1", "创作讨论", 1)),
            messages = listOf(
                ExportMessage("c1", "USER", "你好，世界 🌍\n第二行", 1),
                ExportMessage("c1", "ASSISTANT", "回复内容", 2),
            ),
        )
        listOf(ReadableExportFormatter.markdown(document), ReadableExportFormatter.text(document)).forEach { output ->
            assertTrue(output.contains("你好，世界 🌍\n第二行"))
            assertTrue(output.contains("回复内容"))
            assertFalse(output.contains("apiKey", ignoreCase = true))
            assertFalse(output.contains("reasoning", ignoreCase = true))
            assertFalse(output.contains("file://", ignoreCase = true))
        }
    }
}
