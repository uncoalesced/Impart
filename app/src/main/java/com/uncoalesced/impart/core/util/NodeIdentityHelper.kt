// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class NodeIdentity(val uuid: String, val phoneNumber: String)

/**
 * Resolves this node's advertised identity. The UUID is persisted locally; the phone
 * number is what the handshake QR advertises so a counterpart knows where to send the
 * SMS panic. A missing number is returned as a failure rather than an empty string, so
 * a handshake QR is never generated against an unreachable node.
 */
@Singleton
class NodeIdentityHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePrefsManager: SecurePrefsManager
) {
    fun localUuid(): String = securePrefsManager.getOrCreateLocalUuid()

    // Manual number wins; auto-detect is a best-effort convenience that is null on most
    // modern devices/carriers (and always null with no SIM).
    fun localPhoneNumber(): String? {
        securePrefsManager.getLocalPhoneNumber()?.takeIf { it.isNotBlank() }?.let { return it }
        return autoDetectPhoneNumber()?.takeIf { it.isNotBlank() }
    }

    fun setLocalPhoneNumber(number: String) {
        securePrefsManager.setLocalPhoneNumber(number.trim())
    }

    fun resolve(): Result<NodeIdentity> {
        val uuid = securePrefsManager.getOrCreateLocalUuid()
        val phone = localPhoneNumber()
        return if (phone.isNullOrBlank()) {
            Result.failure(
                IllegalStateException("No phone number set for this node. Enter it on the My QR screen.")
            )
        } else {
            Result.success(NodeIdentity(uuid, phone))
        }
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun autoDetectPhoneNumber(): String? = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        @Suppress("DEPRECATION")
        tm?.line1Number
    } catch (e: Exception) {
        null
    }
}
