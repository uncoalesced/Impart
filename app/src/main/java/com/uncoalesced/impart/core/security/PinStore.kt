// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

// One stored PIN: a random salt and HMAC(key, salt || pin). The PIN itself is never stored.
class PinRecord(val salt: ByteArray, val mac: ByteArray)

class PinRecords(val standard: PinRecord, val duress: PinRecord)

// Where the records live. SecurePrefsManager implements it over the encrypted preferences;
// tests use an in-memory map. Nothing here returns a plaintext PIN except the one-shot
// legacy read that the migration consumes and removes.
interface PinStore {
    fun pinRecords(): PinRecords?

    // Writes both records and marks custom PINs as chosen, in one synchronous commit.
    fun writePinRecords(records: PinRecords)

    // Plaintext PINs written by builds before T02, if any are still present.
    fun legacyPlaintextPins(): LegacyPins?

    // Writes the records and removes the plaintext entries in the same commit.
    fun replaceLegacyPins(records: PinRecords)
}

class LegacyPins(val standard: String, val duress: String)
