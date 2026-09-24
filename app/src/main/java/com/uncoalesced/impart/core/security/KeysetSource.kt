// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.google.crypto.tink.KeysetHandle

// The one seam between CryptoManager and the Android Keystore. Production binds
// KeystoreHelper; the JVM round-trip tests bind an in-memory keyset.
interface KeysetSource {
    fun keysetHandle(): KeysetHandle
}
