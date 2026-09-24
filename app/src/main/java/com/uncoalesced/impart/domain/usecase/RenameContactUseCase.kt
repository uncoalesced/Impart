// Engineered by uncoalesced
package com.uncoalesced.impart.domain.usecase

import com.uncoalesced.impart.domain.repository.ContactRepository
import javax.inject.Inject

class RenameContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(uuid: String, nickname: String): Result<Unit> {
        val trimmed = nickname.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nickname cannot be empty."))
        }
        if (trimmed.length > MAX_LENGTH) {
            return Result.failure(IllegalArgumentException("Nickname is limited to $MAX_LENGTH characters."))
        }
        contactRepository.renameContact(uuid, trimmed)
        return Result.success(Unit)
    }

    private companion object {
        const val MAX_LENGTH = 32
    }
}
