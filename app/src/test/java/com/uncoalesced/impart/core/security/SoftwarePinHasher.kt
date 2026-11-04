// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// JVM stand-in for KeystorePinHasher: the same HMAC-SHA256 over salt || pin, with a random
// in-memory key instead of a Keystore one.
class SoftwarePinHasher : PinHasher {
    private var key: SecretKeySpec? = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "HmacSHA256")
    var deleted = false
        private set

    override fun mac(salt: ByteArray, pinBytes: ByteArray): ByteArray {
        val current = key ?: throw IllegalStateException("PIN MAC key was deleted")
        return Mac.getInstance("HmacSHA256").apply {
            init(current)
            update(salt)
            update(pinBytes)
        }.doFinal()
    }

    override fun deleteKey() {
        key = null
        deleted = true
    }
}

class MemoryPinStore(
    private var legacy: LegacyPins? = null
) : PinStore {
    var records: PinRecords? = null
        private set
    var writes = 0
        private set

    override fun pinRecords(): PinRecords? = records

    override fun writePinRecords(records: PinRecords) {
        this.records = records
        writes++
    }

    override fun legacyPlaintextPins(): LegacyPins? = legacy

    override fun replaceLegacyPins(records: PinRecords) {
        this.records = records
        legacy = null
        writes++
    }
}
