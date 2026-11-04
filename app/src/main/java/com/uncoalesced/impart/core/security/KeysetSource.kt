// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.google.crypto.tink.KeysetHandle

// The one seam between CryptoManager and the Android Keystore. Production binds
// KeystoreHelper; the JVM tests bind in-memory keysets.
interface KeysetSource {
    // Hybrid (HPKE) keyset: alerts are encrypted to its public half.
    fun keysetHandle(): KeysetHandle

    // Ed25519 keyset: every outgoing alert is signed with it.
    fun signingKeysetHandle(): KeysetHandle

    // Drops any cached handles. Called by the duress wipe before the files and the master key go.
    fun forgetKeys() {}

    // Replaces both keysets with new ones and purges the old (T13). Throws on failure.
    fun rotateKeys()
}
