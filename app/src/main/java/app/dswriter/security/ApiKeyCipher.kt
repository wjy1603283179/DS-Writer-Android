package app.dswriter.security

data class EncryptedApiKey(
    val ciphertext: ByteArray,
    val initializationVector: ByteArray,
)

interface ApiKeyCipher {
    fun encrypt(plaintext: ByteArray): EncryptedApiKey

    fun decrypt(encryptedApiKey: EncryptedApiKey): ByteArray
}
