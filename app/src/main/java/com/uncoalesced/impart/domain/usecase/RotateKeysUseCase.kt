// Engineered by uncoalesced
package com.uncoalesced.impart.domain.usecase

import com.uncoalesced.impart.core.security.KeyGeneration
import com.uncoalesced.impart.core.security.KeysetSource
import com.uncoalesced.impart.domain.repository.ContactRepository
import javax.inject.Inject

// Replaces both keysets in one action and flags every active contact as needing a new
// handshake (T13, A2, A7). Old keys are purged, so nothing encrypted to them can be read
// again and nothing can be signed with them, by this phone or by anyone who copied them.
class RotateKeysUseCase @Inject constructor(
    private val keys: KeysetSource,
    private val contacts: ContactRepository,
    private val generation: KeyGeneration
) {
    // Returns how many contacts now need to re-pair.
    suspend operator fun invoke(): Result<Int> = runCatching {
        keys.rotateKeys()
        val flagged = contacts.markAllNeedRepair()
        generation.bump()
        flagged
    }
}
