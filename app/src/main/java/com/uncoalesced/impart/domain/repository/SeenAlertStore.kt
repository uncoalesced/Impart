// Engineered by uncoalesced
package com.uncoalesced.impart.domain.repository

// Remembers the IDs of alerts already accepted, so a copied alert SMS cannot raise the alarm
// twice. markSeen is atomic: it returns false when the ID was already recorded.
interface SeenAlertStore {
    suspend fun markSeen(id: String, now: Long): Boolean
}
