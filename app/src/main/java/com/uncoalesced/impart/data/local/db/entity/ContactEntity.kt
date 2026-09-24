// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.uncoalesced.impart.domain.model.Contact

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val uuid: String,
    val publicKeyBase64: String,
    val nickname: String?,
    val phoneNumber: String?,
    val addedAt: Long,
    val revoked: Boolean
) {
    fun toDomain() = Contact(
        uuid = uuid,
        publicKeyBase64 = publicKeyBase64,
        nickname = nickname,
        phoneNumber = phoneNumber,
        addedAt = addedAt,
        revoked = revoked
    )

    companion object {
        fun fromDomain(contact: Contact) = ContactEntity(
            uuid = contact.uuid,
            publicKeyBase64 = contact.publicKeyBase64,
            nickname = contact.nickname,
            phoneNumber = contact.phoneNumber,
            addedAt = contact.addedAt,
            revoked = contact.revoked
        )
    }
}
