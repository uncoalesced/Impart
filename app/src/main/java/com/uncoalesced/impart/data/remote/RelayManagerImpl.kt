// Engineered by uncoalesced
package com.uncoalesced.impart.data.remote

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.uncoalesced.impart.core.security.AlertEnvelope
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.security.PanicPayloadCodec
import com.uncoalesced.impart.core.services.SmsSentReceiver
import com.uncoalesced.impart.core.util.Redact
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.PanicDispatchReport
import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.model.displayName
import com.uncoalesced.impart.domain.repository.RelayManager
import com.uncoalesced.impart.domain.repository.WallClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// SMS is the only transport. Every alert is signed with this node's Ed25519 key, then
// encrypted to the recipient's public key, and handed to the carrier as an opaque
// IMPART_ALERT text. Each recipient gets its own message id and timestamp.
@Singleton
class RelayManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val securePrefsManager: SecurePrefsManager,
    private val clock: WallClock
) : RelayManager {

    override suspend fun dispatchPanic(contact: Contact, lat: Double?, lng: Double?): Result<Unit> =
        runCatching {
            sendSigned(
                contact,
                PanicPayload.Panic(
                    from = securePrefsManager.localUuid(),
                    to = contact.uuid,
                    id = PanicPayloadCodec.newMessageId(),
                    sentAt = clock.now(),
                    lat = lat,
                    lng = lng
                )
            )
        }.onFailure { Log.e(TAG, "Panic dispatch to node ${Redact.uuid(contact.uuid)} failed", it) }

    override suspend fun broadcastPanic(contacts: List<Contact>, lat: Double?, lng: Double?): PanicDispatchReport {
        var sent = 0
        val failed = mutableListOf<String>()
        for (contact in contacts) {
            dispatchPanic(contact, lat, lng).fold(
                onSuccess = { sent++ },
                onFailure = { failed += "${contact.displayName} (${it.message ?: it.javaClass.simpleName})" }
            )
        }
        return PanicDispatchReport(sent = sent, failed = failed)
    }

    override suspend fun dispatchAck(contact: Contact, ackFor: String): Result<Unit> =
        runCatching {
            sendSigned(
                contact,
                PanicPayload.Ack(
                    from = securePrefsManager.localUuid(),
                    to = contact.uuid,
                    id = PanicPayloadCodec.newMessageId(),
                    sentAt = clock.now(),
                    ackFor = ackFor
                )
            )
        }.onFailure { Log.e(TAG, "Ack to node ${Redact.uuid(contact.uuid)} failed", it) }

    private fun sendSigned(contact: Contact, payload: PanicPayload) {
        send(contact, AlertEnvelope.body(cryptoManager, payload, contact.publicKeyBase64))
    }

    private fun send(contact: Contact, body: String) {
        val target = contact.phoneNumber?.trim().orEmpty()
        if (target.isEmpty()) throw IllegalStateException("Contact has no phone number to page.")

        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            throw IllegalStateException("No SIM is selected for sending SMS.")
        }
        val smsManager = smsManagerFor(subscriptionId)
        val parts = smsManager.divideMessage(body)
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        for (index in parts.indices) {
            sentIntents += SmsSentReceiver.pendingIntent(context, contact.displayName, index, parts.size)
        }
        smsManager.sendMultipartTextMessage(target, null, parts, sentIntents, null)
        Log.i(TAG, "Queued ${parts.size}-part alert to ${Redact.phone(target)} on subscription $subscriptionId")
    }

    private fun smsManagerFor(subscriptionId: Int): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }

    private companion object {
        const val TAG = "RelayManager"
    }
}
