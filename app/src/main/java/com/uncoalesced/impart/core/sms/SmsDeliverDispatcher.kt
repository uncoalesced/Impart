// Engineered by uncoalesced
package com.uncoalesced.impart.core.sms

import com.uncoalesced.impart.domain.repository.SmsStore
import javax.inject.Inject

// What the default-SMS receiver does with one reassembled message. Pure Kotlin so the one
// rule that matters is tested: an alert never reaches the provider.
class SmsDeliverDispatcher @Inject constructor(
    private val store: SmsStore
) {
    sealed interface Outcome {
        data object HandedToVerifier : Outcome
        data class Stored(val address: String) : Outcome
    }

    suspend fun dispatch(
        address: String,
        body: String,
        receivedAt: Long,
        subscriptionId: Int,
        onAlert: suspend (String) -> Unit
    ): Outcome = when (SmsRouter.route(body)) {
        SmsRoute.Alert -> {
            onAlert(body)
            Outcome.HandedToVerifier
        }
        SmsRoute.Ordinary -> {
            store.storeIncoming(address, body, receivedAt, subscriptionId)
            Outcome.Stored(address)
        }
    }
}
