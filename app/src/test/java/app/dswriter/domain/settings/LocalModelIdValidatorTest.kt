package app.dswriter.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalModelIdValidatorTest {
    @Test
    fun `accepts the llama cpp alias style`() {
        assertEquals("novel-a", LocalModelIdValidator.normalize("novel-a"))
        assertEquals("Qwen3.6-35B-A3B", LocalModelIdValidator.normalize("  Qwen3.6-35B-A3B  "))
        assertEquals("novel_b2", LocalModelIdValidator.normalize("novel_b2"))
    }

    @Test
    fun `rejects ids that are not plain aliases`() {
        assertNull(LocalModelIdValidator.normalize(""))
        assertNull(LocalModelIdValidator.normalize("   "))
        assertNull(LocalModelIdValidator.normalize("novel a"))
        assertNull(LocalModelIdValidator.normalize("novel/a"))
        assertNull(LocalModelIdValidator.normalize("novel:a"))
        assertNull(LocalModelIdValidator.normalize("a".repeat(65)))
    }

    @Test
    fun `treats an empty value as clearing the setting`() {
        assertEquals("", LocalModelIdValidator.normalizeOptional(""))
        assertEquals("", LocalModelIdValidator.normalizeOptional("   "))
        assertEquals("novel-a", LocalModelIdValidator.normalizeOptional(" novel-a "))
        assertNull(LocalModelIdValidator.normalizeOptional("novel a"))
    }
}
