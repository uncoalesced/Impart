// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uncoalesced.impart.data.local.db.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE revoked = 0")
    fun getActiveContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE uuid = :uuid LIMIT 1")
    suspend fun getContactByUuid(uuid: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE uuid = :uuid AND revoked = 0 LIMIT 1")
    suspend fun findActiveByUuid(uuid: String): ContactEntity?

    @Query("UPDATE contacts SET nickname = :nickname WHERE uuid = :uuid")
    suspend fun updateNickname(uuid: String, nickname: String)

    @Query("UPDATE contacts SET revoked = 1 WHERE uuid = :uuid")
    suspend fun revokeContact(uuid: String)

    @Query("UPDATE contacts SET needsRepair = 1 WHERE revoked = 0")
    suspend fun markAllNeedRepair(): Int
}
