// Engineered by uncoalesced
package com.uncoalesced.impart.domain.usecase

import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.HandshakePayload
import com.uncoalesced.impart.domain.repository.ContactRepository
import javax.inject.Inject

class AddContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(payload: HandshakePayload) {
        val contact = Contact(
            uuid = payload.uuid,
            publicKeyBase64 = payload.publicKeyBase64,
            phoneNumber = payload.phoneNumber
        )
        contactRepository.addContact(contact)
    }
}
