// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

// Pure JVM on purpose: no android.* imports, so the encrypt/decrypt round trip is unit-tested.
// Every method throws on failure. Callers decide how to surface it; nothing here falls back.
@Singleton
class CryptoManager @Inject constructor(
    private val keys: KeysetSource
) {
    fun getPublicKey(): String {
        val publicKeyset = keys.keysetHandle().publicKeysetHandle
        return encoder.encodeToString(TinkProtoKeysetFormat.serializeKeysetWithoutSecret(publicKeyset))
    }

    fun encrypt(plaintext: String, counterpartPublicKeyBase64: String): String {
        val hybridEncrypt = parsePublicKeyset(counterpartPublicKeyBase64).getPrimitive(HybridEncrypt::class.java)
        val ciphertext = hybridEncrypt.encrypt(plaintext.toByteArray(Charsets.UTF_8), EMPTY_CONTEXT)
        return encoder.encodeToString(ciphertext)
    }

    fun decrypt(ciphertextBase64: String): String {
        val hybridDecrypt = keys.keysetHandle().getPrimitive(HybridDecrypt::class.java)
        val plaintext = hybridDecrypt.decrypt(decode(ciphertextBase64), EMPTY_CONTEXT)
        return String(plaintext, Charsets.UTF_8)
    }

    fun isValidPublicKey(publicKeyBase64: String): Boolean = runCatching {
        parsePublicKeyset(publicKeyBase64).getPrimitive(HybridEncrypt::class.java)
    }.isSuccess

    private fun parsePublicKeyset(base64: String): KeysetHandle =
        TinkProtoKeysetFormat.parseKeysetWithoutSecret(decode(base64))

    // Strict decoder, but whitespace-tolerant: an SMS body can pick up a stray space.
    private fun decode(base64: String): ByteArray =
        decoder.decode(base64.filterNot { it.isWhitespace() })

    private companion object {
        val EMPTY_CONTEXT = ByteArray(0)
        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
