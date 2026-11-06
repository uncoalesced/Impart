// Engineered by uncoalesced
package com.uncoalesced.impart.domain.repository

import com.uncoalesced.impart.domain.model.SmsConversation
import com.uncoalesced.impart.domain.model.SmsMessageItem
import kotlinx.coroutines.flow.Flow

// The phone's SMS provider, as a default SMS app must use it. The alert pipeline never calls
// this: SmsDeliverDispatcher routes an alert to the verifier before any store call.
interface SmsStore {
    suspend fun storeIncoming(address: String, body: String, receivedAt: Long, subscriptionId: Int)
    suspend fun storeOutgoing(address: String, body: String, sentAt: Long, subscriptionId: Int)
    fun conversations(): Flow<List<SmsConversation>>
    fun thread(address: String): Flow<List<SmsMessageItem>>
    suspend fun markRead(address: String)
}
