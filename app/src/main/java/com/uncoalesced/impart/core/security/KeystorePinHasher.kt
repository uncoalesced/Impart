// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

// A non-exportable HmacSHA256 key under the alias impart_pin_mac. StrongBox is requested
// first and the TEE is the fallback. Generation and use throw on failure: a phone that
// cannot hold the key cannot verify PINs, and that must not fail quietly into "unlocked".
@Singleton
class KeystorePinHasher @Inject constructor() : PinHasher {

    override fun mac(salt: ByteArray, pinBytes: ByteArray): ByteArray {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(loadOrCreateKey())
        mac.update(salt)
        mac.update(pinBytes)
        return mac.doFinal()
    }

    override fun deleteKey() {
        synchronized(this) {
            try {
                val keyStore = keyStore()
                if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete the PIN MAC key", e)
            }
        }
    }

    private fun loadOrCreateKey(): SecretKey = synchronized(this) {
        val keyStore = keyStore()
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generate(strongBox = true)
            } catch (e: StrongBoxUnavailableException) {
                Log.w(TAG, "StrongBox unavailable, PIN MAC key falls back to the TEE")
            }
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setKeySize(KEY_BITS)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        private const val TAG = "KeystorePinHasher"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MAC_ALGORITHM = "HmacSHA256"
        private const val KEY_BITS = 256
        const val ALIAS = "impart_pin_mac"
    }
}
