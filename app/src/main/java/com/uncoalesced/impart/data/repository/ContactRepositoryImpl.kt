// Engineered by uncoalesced
package com.uncoalesced.impart.data.repository

import com.uncoalesced.impart.data.local.db.ContactDao
import com.uncoalesced.impart.data.local.db.entity.ContactEntity
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao
) : ContactRepository {
    override suspend fun addContact(contact: Contact) {
        contactDao.insertOrUpdate(ContactEntity.fromDomain(contact))
    }

    override fun getActiveContacts(): Flow<List<Contact>> =
        contactDao.getActiveContacts().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getContactByUuid(uuid: String): Contact? =
        contactDao.getContactByUuid(uuid)?.toDomain()

    override suspend fun findActiveByUuid(uuid: String): Contact? =
        contactDao.findActiveByUuid(uuid)?.toDomain()

    override suspend fun renameContact(uuid: String, nickname: String) {
        contactDao.updateNickname(uuid, nickname)
    }

    override suspend fun revokeContact(uuid: String) {
        contactDao.revokeContact(uuid)
    }
}
