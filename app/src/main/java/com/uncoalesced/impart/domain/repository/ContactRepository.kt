// Engineered by uncoalesced
package com.uncoalesced.impart.domain.repository

import com.uncoalesced.impart.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    suspend fun addContact(contact: Contact)
    fun getActiveContacts(): Flow<List<Contact>>
    suspend fun getContactByUuid(uuid: String): Contact?
    suspend fun findActiveByPublicKey(publicKeyBase64: String): Contact?
    suspend fun renameContact(uuid: String, nickname: String)
    suspend fun revokeContact(uuid: String)
}
