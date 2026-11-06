// Engineered by uncoalesced
package com.uncoalesced.impart.domain.usecase

import com.uncoalesced.impart.domain.rules.PhoneNumbers
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.HandshakePayload
import com.uncoalesced.impart.domain.repository.ContactRepository
import javax.inject.Inject

sealed interface AddContactResult {
    data class Added(val contact: Contact) : AddContactResult

    // Same node, same keys: an intentional re-pair. Nickname and pairing date are kept and a
    // revoked contact becomes active again.
    data class Refreshed(val contact: Contact) : AddContactResult

    // A known UUID with different keys. Nothing is written until the user confirms, because
    // this is exactly what a QR built to take over an existing contact looks like.
    data class KeyChanged(val existing: Contact, val candidate: Contact) : AddContactResult
}

class AddContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(payload: HandshakePayload, localUuid: String): Result<AddContactResult> {
        if (payload.uuid.isBlank() || payload.publicKeyBase64.isBlank() || payload.signingKeyBase64.isBlank()) {
            return Result.failure(IllegalArgumentException("Handshake payload is missing its UUID or a key."))
        }
        val phone = PhoneNumbers.normalize(payload.phoneNumber)
        if (!PhoneNumbers.isValidE164(phone)) {
            return Result.failure(
                IllegalArgumentException("The number in this QR is not a full international number. It must start with + and a country code.")
            )
        }
        if (payload.uuid == localUuid) {
            return Result.failure(IllegalArgumentException("You scanned this node's own QR code."))
        }

        val candidate = Contact(
            uuid = payload.uuid,
            publicKeyBase64 = payload.publicKeyBase64,
            signingKeyBase64 = payload.signingKeyBase64,
            phoneNumber = phone
        )
        val existing = contactRepository.getContactByUuid(payload.uuid)
        val result = when {
            existing == null -> {
                contactRepository.addContact(candidate)
                AddContactResult.Added(candidate)
            }
            existing.publicKeyBase64 != candidate.publicKeyBase64 ||
                existing.signingKeyBase64 != candidate.signingKeyBase64 ->
                AddContactResult.KeyChanged(existing, candidate)
            else -> {
                // A fresh handshake also clears the flag a key rotation set.
                val refreshed = existing.copy(phoneNumber = phone, revoked = false, needsRepair = false)
                contactRepository.addContact(refreshed)
                AddContactResult.Refreshed(refreshed)
            }
        }
        return Result.success(result)
    }

    // Called only after the user has seen both fingerprints and confirmed in person.
    suspend fun confirmKeyChange(existing: Contact, candidate: Contact): Contact {
        val replaced = candidate.copy(nickname = existing.nickname, revoked = false, needsRepair = false)
        contactRepository.addContact(replaced)
        return replaced
    }
}
