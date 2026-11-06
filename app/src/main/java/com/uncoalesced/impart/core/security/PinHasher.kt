// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

// Keyed hash over a PIN. Production uses an HMAC key that lives in the Android Keystore and
// cannot be exported, so a stolen preferences file cannot be brute-forced off the phone.
// JVM tests bind a software HMAC with a random key.
interface PinHasher {
    // HMAC(key, salt || pinBytes). The caller zeroes pinBytes afterwards.
    fun mac(salt: ByteArray, pinBytes: ByteArray): ByteArray

    // Deletes the key. Part of the duress wipe: without the key no stored record can ever
    // be checked again.
    fun deleteKey()
}
