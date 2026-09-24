// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.TinkProtoKeysetFormat
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

// Pure JVM on purpose: no android.* imports, so every primitive is unit-tested.
// encrypt, decrypt and sign throw on failure; verify answers false. Nothing falls back.
@Singleton
class CryptoManager @Inject constructor(
    private val keys: KeysetSource
) {
    fun getPublicKey(): String = exportPublic(keys.keysetHandle())

    fun getSigningPublicKey(): String = exportPublic(keys.signingKeysetHandle())

    fun encrypt(plaintext: String, counterpartPublicKeyBase64: String): String {
        val hybridEncrypt = parsePublic(counterpartPublicKeyBase64).getPrimitive(HybridEncrypt::class.java)
        return encoder.encodeToString(hybridEncrypt.encrypt(plaintext.toByteArray(Charsets.UTF_8), EMPTY_CONTEXT))
    }

    fun decrypt(ciphertextBase64: String): String {
        val hybridDecrypt = keys.keysetHandle().getPrimitive(HybridDecrypt::class.java)
        return String(hybridDecrypt.decrypt(decode(ciphertextBase64), EMPTY_CONTEXT), Charsets.UTF_8)
    }

    fun sign(data: ByteArray): String =
        encoder.encodeToString(keys.signingKeysetHandle().getPrimitive(PublicKeySign::class.java).sign(data))

    // False for a bad signature, a malformed signature, or a key that is not a signing key.
    fun verify(signatureBase64: String, data: ByteArray, signingKeyBase64: String): Boolean = try {
        parsePublic(signingKeyBase64).getPrimitive(PublicKeyVerify::class.java).verify(decode(signatureBase64), data)
        true
    } catch (e: GeneralSecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    fun isValidPublicKey(publicKeyBase64: String): Boolean = runCatching {
        parsePublic(publicKeyBase64).getPrimitive(HybridEncrypt::class.java)
    }.isSuccess

    fun isValidSigningKey(signingKeyBase64: String): Boolean = runCatching {
        parsePublic(signingKeyBase64).getPrimitive(PublicKeyVerify::class.java)
    }.isSuccess

    private fun exportPublic(handle: KeysetHandle): String =
        encoder.encodeToString(TinkProtoKeysetFormat.serializeKeysetWithoutSecret(handle.publicKeysetHandle))

    private fun parsePublic(base64: String): KeysetHandle =
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
