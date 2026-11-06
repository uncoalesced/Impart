// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import java.security.MessageDigest

// Tells a debug-signed build from a release. The debug keystore is committed to the
// repository, so anyone can sign an "update" with it that installs over a debug build and
// inherits its data (A1-7, A8-4). The readiness card and the deployment workflow both check
// the certificate, not only BuildConfig.DEBUG.
object SigningIdentity {
    // SHA-256 of the certificate in debug.keystore at the repository root. SigningIdentityTest
    // recomputes it from the real file.
    const val DEBUG_CERT_SHA256 = "5a5a07b6f236d8986737f727a51297f7cbc11895a2a84f29e57cf8b510bd0ad5"

    fun sha256Hex(certificateDer: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificateDer).joinToString("") { "%02x".format(it) }

    fun isDebugCertificate(certificateDer: ByteArray): Boolean = sha256Hex(certificateDer) == DEBUG_CERT_SHA256

    // Short form shown on the About screen so two phones can compare builds by eye.
    fun shortFingerprint(certificateDer: ByteArray): String =
        sha256Hex(certificateDer).take(16).chunked(4).joinToString(" ").uppercase()
}
