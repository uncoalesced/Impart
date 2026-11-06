// Engineered by uncoalesced
package com.uncoalesced.impart.domain.usecase

import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeContactRepository : ContactRepository {
    private val store = MutableStateFlow<Map<String, Contact>>(emptyMap())

    val contacts: List<Contact> get() = store.value.values.toList()

    override suspend fun addContact(contact: Contact) {
        store.value = store.value + (contact.uuid to contact)
    }

    override fun getActiveContacts(): Flow<List<Contact>> =
        store.map { it.values.filterNot { c -> c.revoked } }

    override suspend fun getContactByUuid(uuid: String): Contact? = store.value[uuid]

    override suspend fun findActiveByUuid(uuid: String): Contact? = store.value[uuid]?.takeIf { !it.revoked }

    override suspend fun renameContact(uuid: String, nickname: String) {
        store.value[uuid]?.let { store.value = store.value + (uuid to it.copy(nickname = nickname)) }
    }

    override suspend fun revokeContact(uuid: String) {
        store.value[uuid]?.let { store.value = store.value + (uuid to it.copy(revoked = true)) }
    }

    override suspend fun markAllNeedRepair(): Int {
        val active = store.value.values.filterNot { it.revoked }
        store.value = store.value + active.associate { it.uuid to it.copy(needsRepair = true) }
        return active.size
    }
}
