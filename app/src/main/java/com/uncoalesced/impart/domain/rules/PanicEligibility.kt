// Engineered by uncoalesced
package com.uncoalesced.impart.domain.rules

import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.displayName

// Whether an alert may be sent to a contact at all. A contact flagged after a key rotation
// still holds this node's old keys: an alert would reach them and fail their signature check,
// so it is refused here with a reason the user sees (T13).
object PanicEligibility {
    fun check(contact: Contact): Result<Unit> = when {
        contact.revoked -> Result.failure(IllegalStateException("${contact.displayName} is revoked."))
        contact.needsRepair -> Result.failure(
            IllegalStateException("${contact.displayName} must scan your new QR before you can page them: your keys were rotated.")
        )
        contact.phoneNumber.isNullOrBlank() -> Result.failure(IllegalStateException("${contact.displayName} has no phone number to page."))
        else -> Result.success(Unit)
    }
}
