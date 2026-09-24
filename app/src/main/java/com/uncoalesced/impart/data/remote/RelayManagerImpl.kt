// Engineered by uncoalesced
package com.uncoalesced.impart.data.remote

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.security.IMPART_ALERT_PREFIX
import com.uncoalesced.impart.core.security.PanicPayloadCodec
import com.uncoalesced.impart.core.services.SmsSentReceiver
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.PanicDispatchReport
import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.model.displayName
import com.uncoalesced.impart.domain.repository.RelayManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// SMS is the only transport. Every panic and ack is Tink-encrypted here and handed to the
// carrier as an opaque IMPART_ALERT text; nothing else ever leaves the device.
@Singleton
class RelayManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val securePrefsManager: SecurePrefsManager
) : RelayManager {

    override suspend fun dispatchPanic(contact: Contact, lat: Double?, lng: Double?): Result<Unit> =
        runCatching {
            val payload = PanicPayload.Panic(
                senderPhoneNumber = securePrefsManager.localPhoneNumber.value,
                senderPublicKeyBase64 = cryptoManager.getPublicKey(),
                lat = lat,
                lng = lng
            )
            send(contact.phoneNumber, PanicPayloadCodec.encode(payload), contact.publicKeyBase64)
        }.onFailure { Log.e(TAG, "Panic dispatch to ${contact.displayName} failed", it) }

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

    override suspend fun dispatchAck(targetPhoneNumber: String, counterpartPublicKeyBase64: String): Result<Unit> =
        runCatching {
            send(targetPhoneNumber, PanicPayloadCodec.encode(PanicPayload.Ack), counterpartPublicKeyBase64)
        }.onFailure { Log.e(TAG, "Ack dispatch failed", it) }

    private fun send(phoneNumber: String?, plaintext: String, counterpartPublicKeyBase64: String) {
        val target = phoneNumber?.trim().orEmpty()
        if (target.isEmpty()) {
            throw IllegalStateException("Contact has no phone number to page.")
        }
        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            throw IllegalStateException("No SIM is selected for sending SMS.")
        }
        val smsManager = smsManagerFor(subscriptionId)
        val body = IMPART_ALERT_PREFIX + cryptoManager.encrypt(plaintext, counterpartPublicKeyBase64)
        val parts = smsManager.divideMessage(body)
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        for (index in parts.indices) {
            sentIntents += SmsSentReceiver.pendingIntent(context, target, index, parts.size)
        }
        smsManager.sendMultipartTextMessage(target, null, parts, sentIntents, null)
        Log.i(TAG, "Queued ${parts.size}-part IMPART_ALERT SMS on subscription $subscriptionId")
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
