// Engineered by uncoalesced
package com.uncoalesced.impart.domain.usecase

import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.HandshakePayload
import com.uncoalesced.impart.domain.repository.ContactRepository
import javax.inject.Inject

class AddContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(payload: HandshakePayload, localUuid: String): Result<Contact> {
        if (payload.uuid.isBlank() || payload.publicKeyBase64.isBlank()) {
            return Result.failure(IllegalArgumentException("Handshake payload is missing its UUID or public key."))
        }
        if (payload.phoneNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("Handshake payload carries no phone number to page."))
        }
        if (payload.uuid == localUuid) {
            return Result.failure(IllegalArgumentException("You scanned this node's own QR code."))
        }
        val contact = Contact(
            uuid = payload.uuid,
            publicKeyBase64 = payload.publicKeyBase64,
            phoneNumber = payload.phoneNumber.trim()
        )
        contactRepository.addContact(contact)
        return Result.success(contact)
    }
}
