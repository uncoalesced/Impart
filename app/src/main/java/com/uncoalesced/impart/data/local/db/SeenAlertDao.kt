// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uncoalesced.impart.data.local.db.entity.SeenAlertEntity

@Dao
interface SeenAlertDao {
    // Returns the new row id, or -1 when the id was already present.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SeenAlertEntity): Long

    @Query("DELETE FROM seen_alerts WHERE receivedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
