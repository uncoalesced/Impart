// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "seen_alerts")
data class SeenAlertEntity(
    @PrimaryKey val id: String,
    val receivedAt: Long
)
