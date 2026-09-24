// Engineered by uncoalesced
package com.uncoalesced.impart.domain.model

data class Contact(
    val uuid: String,
    val publicKeyBase64: String,
    val signingKeyBase64: String,
    val nickname: String? = null,
    val phoneNumber: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val revoked: Boolean = false
)

// Nickname first, then the number the contact is paged at, then a UUID stub. A row that
// only says UNKNOWN NODE is useless when three phones are on the table.
val Contact.displayName: String
    get() = nickname?.takeIf { it.isNotBlank() }
        ?: phoneNumber?.takeIf { it.isNotBlank() }
        ?: uuid.take(8)
