// Engineered by uncoalesced
package com.uncoalesced.impart.core.services

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.security.IMPART_ALERT_PREFIX
import com.uncoalesced.impart.core.security.PanicPayloadCodec
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.model.displayName
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.presentation.intrusion.IntrusionActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// The brain of the intrusion pipeline. An incoming SMS wakes this receiver even from Doze;
// everything after the prefix match runs off the main thread under a wake lock, and every
// failure path ends in something visible. A dropped alert with no trace is the one outcome
// this class must never produce.
@AndroidEntryPoint
class ImpartSmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var cryptoManager: CryptoManager

    @Inject
    lateinit var contactRepository: ContactRepository

    @Inject
    lateinit var duressManager: DuressManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        if (!body.startsWith(IMPART_ALERT_PREFIX)) return

        if (duressManager.isCorrupted.value) {
            Log.w(TAG, "Alert ignored: this node has been wiped")
            return
        }

        val appContext = context.applicationContext
        val wakeLock = appContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        val pendingResult = goAsync()

        // A manifest receiver has no lifecycle owner, so this scope is deliberately
        // unstructured; the wake lock timeout is the upper bound on its life.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleAlert(appContext, body.removePrefix(IMPART_ALERT_PREFIX))
            } catch (t: Throwable) {
                Log.e(TAG, "Intrusion pipeline failed", t)
                ImpartNotifications.notifyHeadsUp(
                    appContext,
                    ImpartNotifications.CHANNEL_STATUS,
                    ImpartNotifications.ID_UNREADABLE,
                    "ALERT PIPELINE FAILURE",
                    "An Impart alert arrived but could not be processed: ${t.message ?: t.javaClass.simpleName}"
                )
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlert(context: Context, ciphertext: String) {
        val plaintext = try {
            cryptoManager.decrypt(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed for an incoming alert", e)
            ImpartNotifications.notifyHeadsUp(
                context,
                ImpartNotifications.CHANNEL_STATUS,
                ImpartNotifications.ID_UNREADABLE,
                "UNREADABLE ALERT RECEIVED",
                "An Impart alert arrived that this node could not decrypt. It was not encrypted to this node's key, or it was damaged in transit."
            )
            return
        }

        val payload = PanicPayloadCodec.decode(plaintext).getOrElse { e ->
            Log.e(TAG, "Malformed alert payload", e)
            ImpartNotifications.notifyHeadsUp(
                context,
                ImpartNotifications.CHANNEL_STATUS,
                ImpartNotifications.ID_UNREADABLE,
                "MALFORMED ALERT RECEIVED",
                "An Impart alert decrypted but its contents were not understood: ${e.message}"
            )
            return
        }

        when (payload) {
            is PanicPayload.Panic -> {
                val sender = contactRepository.findActiveByPublicKey(payload.senderPublicKeyBase64)
                if (sender == null) {
                    Log.w(TAG, "Panic from a node that is not in the Contact Matrix; alarm withheld")
                    ImpartNotifications.notifyHeadsUp(
                        context,
                        ImpartNotifications.CHANNEL_STATUS,
                        ImpartNotifications.ID_UNKNOWN_SENDER,
                        "PANIC FROM UNRECOGNIZED NODE",
                        "A node that is not in your Contact Matrix sent a panic signal. Pair it by QR handshake before it can raise the alarm."
                    )
                    return
                }
                Log.i(TAG, "Panic from trusted node ${sender.uuid.take(8)}; firing intrusion")
                fireIntrusion(context, sender, payload)
            }

            PanicPayload.Ack -> ImpartNotifications.notifyHeadsUp(
                context,
                ImpartNotifications.CHANNEL_ACK,
                ImpartNotifications.ID_ACK,
                "PANIC ACKNOWLEDGED",
                "A trusted node acknowledged your panic signal."
            )
        }
    }

    private fun fireIntrusion(context: Context, sender: Contact, payload: PanicPayload.Panic) {
        val intrusionIntent = IntrusionActivity.createIntent(
            context = context,
            senderName = sender.displayName,
            senderPhoneNumber = payload.senderPhoneNumber ?: sender.phoneNumber,
            senderPublicKeyBase64 = payload.senderPublicKeyBase64,
            lat = payload.lat,
            lng = payload.lng
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intrusionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, ImpartNotifications.CHANNEL_PANIC)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("IMPART: PANIC SIGNAL")
            .setContentText("Incoming panic from ${sender.displayName}. Tap to open.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
        context.getSystemService(android.app.NotificationManager::class.java)
            .notify(ImpartNotifications.ID_PANIC, notification)
    }

    private companion object {
        const val TAG = "ImpartSmsReceiver"
        const val WAKE_LOCK_TAG = "Impart::SmsWakeLock"
        const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
