// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import com.google.firebase.messaging.FirebaseMessaging
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class NodeIdentity(val uuid: String, val fcmToken: String)

/**
 * Resolves this node's advertised identity. The UUID is persisted locally; the FCM token
 * is fetched live. A missing token is returned as a failure rather than an empty string,
 * so a handshake QR is never generated against an unroutable token.
 */
@Singleton
class NodeIdentityHelper @Inject constructor(
    private val securePrefsManager: SecurePrefsManager
) {
    fun localUuid(): String = securePrefsManager.getOrCreateLocalUuid()

    suspend fun resolve(): Result<NodeIdentity> {
        val uuid = securePrefsManager.getOrCreateLocalUuid()
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isNullOrBlank()) {
                Result.failure(IllegalStateException("FCM token unavailable on this node."))
            } else {
                Result.success(NodeIdentity(uuid, token))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
