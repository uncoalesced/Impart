// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import android.content.Context
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.signature.SignatureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
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

    // Drops the in-memory handles. The preferences file and the master key alias are deleted
    // by DuressWiper from WipeInventory, which is the single list of what the wipe removes.
    override fun forgetKeys() {
        synchronized(this) {
            hybrid = null
            signing = null
        }
    }

    companion object {
        private const val HYBRID_KEYSET_NAME = "impart_keyset"
        private const val SIGNING_KEYSET_NAME = "impart_signing_keyset"
        private const val PREF_FILE_NAME = WipeInventory.TINK_KEYSET_PREFS
        private const val MASTER_KEY_URI = "android-keystore://${WipeInventory.TINK_MASTER_KEY_ALIAS}"
        const val HYBRID_KEY_TEMPLATE = "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"
        const val SIGNING_KEY_TEMPLATE = "ED25519"
    }
}
