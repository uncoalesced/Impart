// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

enum class PinMatch { STANDARD, DURESS, NONE }

// Pure Kotlin. Verifies a PIN against the salted keyed hashes in the PinStore and never
// keeps a plaintext PIN. Both records are compared on every call with a constant-time
// comparison, so the answer does not leak which digits were right.
@Singleton
class PinVerifier @Inject constructor(
    private val store: PinStore,
    private val hasher: PinHasher
) {
    private val random = SecureRandom()

    // Seam for the test that proves the constant-time comparison is the one in use.
    internal var compare: (ByteArray, ByteArray) -> Boolean = MessageDigest::isEqual

    fun hasPins(): Boolean {
        migrateLegacyIfNeeded()
        return store.pinRecords() != null
    }

    fun classify(pin: CharArray): PinMatch {
        migrateLegacyIfNeeded()
        val records = store.pinRecords() ?: return PinMatch.NONE
        val standard = matches(records.standard, pin)
        val duress = matches(records.duress, pin)
        return when {
            standard -> PinMatch.STANDARD
            duress -> PinMatch.DURESS
            else -> PinMatch.NONE
        }
    }

    // Stores both PINs. Refuses a duress PIN that verifies against the new standard record:
    // the check runs through the same hash the lock screen will use.
    fun enroll(standard: CharArray, duress: CharArray): Result<Unit> {
        migrateLegacyIfNeeded()
        val standardRecord = record(standard)
        if (matches(standardRecord, duress)) {
            return Result.failure(IllegalArgumentException("The duress PIN must differ from the standard PIN."))
        }
        store.writePinRecords(PinRecords(standardRecord, record(duress)))
        return Result.success(Unit)
    }

    private fun migrateLegacyIfNeeded() {
        val legacy = store.legacyPlaintextPins() ?: return
        val standard = legacy.standard.toCharArray()
        val duress = legacy.duress.toCharArray()
        try {
            store.replaceLegacyPins(PinRecords(record(standard), record(duress)))
        } finally {
            standard.fill('\u0000')
            duress.fill('\u0000')
        }
    }

    private fun record(pin: CharArray): PinRecord {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        return PinRecord(salt, mac(salt, pin))
    }

    private fun matches(record: PinRecord, pin: CharArray): Boolean = compare(record.mac, mac(record.salt, pin))

    private fun mac(salt: ByteArray, pin: CharArray): ByteArray {
        val bytes = ByteArray(pin.size) { pin[it].code.toByte() }
        try {
            return hasher.mac(salt, bytes)
        } finally {
            bytes.fill(0)
        }
    }

    companion object {
        const val SALT_BYTES = 16
    }
}
