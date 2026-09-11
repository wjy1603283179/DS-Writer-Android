package app.dswriter.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreApiKeyCipherTest {
    @Test
    fun encryptsAndDecryptsWithoutPersistingPlaintext() {
        val cipher = AndroidKeystoreApiKeyCipher()
        val plaintext = "instrumentation-test-key".toByteArray(StandardCharsets.UTF_8)

        val encrypted = cipher.encrypt(plaintext)

        assertFalse(encrypted.ciphertext.contentEquals(plaintext))
        assertArrayEquals(plaintext, cipher.decrypt(encrypted))
    }
}
