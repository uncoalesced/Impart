// Engineered by uncoalesced
package com.uncoalesced.impart.data.repository

import com.uncoalesced.impart.data.local.db.SeenAlertDao
import com.uncoalesced.impart.data.local.db.entity.SeenAlertEntity
import com.uncoalesced.impart.domain.repository.SeenAlertStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeenAlertStoreImpl @Inject constructor(
    private val dao: SeenAlertDao
) : SeenAlertStore {

    // The primary key makes the insert the atomic test: a duplicate id is ignored and the
    // DAO reports -1. Pruning keeps the table to two days, twice AlertVerifier's age limit,
    // so anything old enough to be pruned is already rejected as stale.
    override suspend fun markSeen(id: String, now: Long): Boolean {
        dao.deleteOlderThan(now - RETENTION_MS)
        return dao.insert(SeenAlertEntity(id = id, receivedAt = now)) != -1L
    }

    private companion object {
        const val RETENTION_MS = 48 * 60 * 60 * 1000L
    }
}
