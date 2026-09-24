// Engineered by uncoalesced
package com.uncoalesced.impart.domain.model

// Plaintext of an alert, signed by the sender and then encrypted to the recipient.
// from/to bind the alert to one sender and one recipient; id and sentAt make every alert
// unique and dated, which is what the replay defence checks.
sealed interface PanicPayload {
    val from: String
    val to: String
    val id: String
    val sentAt: Long

    data class Panic(
        override val from: String,
        override val to: String,
        override val id: String,
        override val sentAt: Long,
        val lat: Double?,
        val lng: Double?
    ) : PanicPayload

    data class Ack(
        override val from: String,
        override val to: String,
        override val id: String,
        override val sentAt: Long,
        val ackFor: String
    ) : PanicPayload
}
