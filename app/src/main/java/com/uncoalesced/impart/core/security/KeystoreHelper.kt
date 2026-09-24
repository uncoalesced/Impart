// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import android.content.Context
import android.util.Log
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.signature.SignatureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreHelper @Inject constructor(
    @ApplicationContext private val context: Context
) : KeysetSource {

    init {
        HybridConfig.register()
        SignatureConfig.register()
    }

    @Volatile
    private var hybrid: KeysetHandle? = null

    @Volatile
    private var signing: KeysetHandle? = null

    // Each keyset is built once per process. The SMS receiver decrypts and verifies on a 30 s
    // wake lock, so it must not pay for a prefs read and Keystore unwrap on every alert.
    override fun keysetHandle(): KeysetHandle =
        hybrid ?: synchronized(this) { hybrid ?: load(HYBRID_KEYSET_NAME, HYBRID_KEY_TEMPLATE).also { hybrid = it } }

    override fun signingKeysetHandle(): KeysetHandle =
        signing ?: synchronized(this) { signing ?: load(SIGNING_KEYSET_NAME, SIGNING_KEY_TEMPLATE).also { signing = it } }

    private fun load(keysetName: String, template: String): KeysetHandle =
        AndroidKeysetManager.Builder()
            .withSharedPref(context, keysetName, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get(template))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

    // Both keysets live in the same prefs file, so one clear() removes both.
    fun purgeKeys() {
        synchronized(this) {
            hybrid = null
            signing = null
            try {
                context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear the Tink keyset preferences", e)
            }
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                keyStore.load(null)
                if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                    keyStore.deleteEntry(MASTER_KEY_ALIAS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete the Keystore master key", e)
            }
        }
    }

    companion object {
        private const val TAG = "KeystoreHelper"
        private const val HYBRID_KEYSET_NAME = "impart_keyset"
        private const val SIGNING_KEYSET_NAME = "impart_signing_keyset"
        private const val PREF_FILE_NAME = "impart_key_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "impart_master_key"
        private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
        const val HYBRID_KEY_TEMPLATE = "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"
        const val SIGNING_KEY_TEMPLATE = "ED25519"
    }
}
