// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import android.content.Context
import android.util.Log
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
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
    }

    @Volatile
    private var cached: KeysetHandle? = null

    // Built once per process. The SMS receiver decrypts on a 30 s wake lock, so it must not
    // pay for a fresh AndroidKeysetManager (prefs read + Keystore unwrap) on every alert.
    override fun keysetHandle(): KeysetHandle {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val handle = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
                .withKeyTemplate(KeyTemplates.get(KEY_TEMPLATE))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            cached = handle
            return handle
        }
    }

    fun purgeKeys() {
        synchronized(this) {
            cached = null
            try {
                context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE).edit().clear().apply()
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
        private const val KEYSET_NAME = "impart_keyset"
        private const val PREF_FILE_NAME = "impart_key_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "impart_master_key"
        private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
        const val KEY_TEMPLATE = "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"
    }
}
