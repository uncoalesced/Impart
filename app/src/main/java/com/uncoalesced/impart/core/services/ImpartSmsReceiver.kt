// Engineered by uncoalesced
package com.uncoalesced.impart.core.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.uncoalesced.impart.core.security.AlertVerdict
import com.uncoalesced.impart.core.security.AlertVerifier
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.security.IMPART_ALERT_PREFIX
import com.uncoalesced.impart.core.util.Redact
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.model.displayName
import com.uncoalesced.impart.presentation.intrusion.IntrusionActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// The brain of the intrusion pipeline. An incoming SMS wakes this receiver even from Doze;
// everything after the prefix match runs off the main thread under a wake lock. AlertVerifier
// decides; this class makes every verdict visible. Only an authenticated, fresh, first-seen
// panic from a trusted contact raises the alarm, and nothing else is ever silent.
@AndroidEntryPoint
class ImpartSmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alertVerifier: AlertVerifier

    @Inject
    lateinit var securePrefsManager: SecurePrefsManager

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
                val verdict = alertVerifier.verify(body.removePrefix(IMPART_ALERT_PREFIX), securePrefsManager.localUuid())
                handle(appContext, verdict)
            } catch (t: Throwable) {
                Log.e(TAG, "Intrusion pipeline failed", t)
                status(
                    appContext, ImpartNotifications.ID_UNREADABLE, "ALERT PIPELINE FAILURE",
                    "An Impart alert arrived but could not be processed: ${t.message ?: t.javaClass.simpleName}"
                )
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private fun handle(context: Context, verdict: AlertVerdict) {
        Log.i(TAG, "Alert verdict: ${verdict.javaClass.simpleName}")
        when (verdict) {
            AlertVerdict.Unreadable -> status(
                context, ImpartNotifications.ID_UNREADABLE, "UNREADABLE ALERT RECEIVED",
                "An Impart alert arrived that this node could not decrypt. It was not encrypted to this node's key, or it was damaged in transit."
            )
            AlertVerdict.Malformed -> status(
                context, ImpartNotifications.ID_UNREADABLE, "MALFORMED ALERT RECEIVED",
                "An Impart alert decrypted but its contents were not understood. No alarm was raised."
            )
            AlertVerdict.Outdated -> status(
                context, ImpartNotifications.ID_UNREADABLE, "ALERT FROM AN OUTDATED VERSION",
                "An Impart alert arrived in the old unsigned format. Update Impart on both phones and pair again."
            )
            AlertVerdict.UnknownSender -> status(
                context, ImpartNotifications.ID_UNKNOWN_SENDER, "ALERT FROM UNRECOGNIZED NODE",
                "A node that is not in your Contact Matrix sent an alert. Pair it by QR handshake before it can raise the alarm."
            )
            is AlertVerdict.BadSignature -> security(
                context, "ALERT FAILED VERIFICATION",
                "An alert claiming to be from ${verdict.claimedSender.displayName} failed its signature check. Someone may be impersonating them. No alarm was raised."
            )
            is AlertVerdict.NotForThisNode -> security(
                context, "MISDIRECTED ALERT",
                "${verdict.sender.displayName} sent an alert addressed to a different node. It was not raised."
            )
            is AlertVerdict.FromFuture -> security(
                context, "ALERT WITH AN IMPOSSIBLE TIME",
                "An alert from ${verdict.sender.displayName} is dated ${format(verdict.sentAt)}, which is in the future. Check this phone's clock. No alarm was raised."
            )
            is AlertVerdict.Stale -> status(
                context, ImpartNotifications.ID_STALE, "OLD ALERT NOT RAISED",
                "${kind(verdict.payload)} from ${verdict.sender.displayName} sent on ${format(verdict.payload.sentAt)} arrived more than a day late and was not raised."
            )
            is AlertVerdict.Replayed -> security(
                context, "REPEATED ALERT IGNORED",
                "An alert from ${verdict.sender.displayName} that was already handled arrived again. It may have been copied and re-sent. No alarm was raised."
            )
            is AlertVerdict.Accepted -> when (val payload = verdict.payload) {
                is PanicPayload.Panic -> {
                    Log.i(TAG, "Panic from trusted node ${Redact.uuid(verdict.sender.uuid)}; firing intrusion")
                    fireIntrusion(context, verdict.sender, payload, verdict.delayMinutes)
                }
                is PanicPayload.Ack -> ImpartNotifications.notifyHeadsUp(
                    context, ImpartNotifications.CHANNEL_ACK, ImpartNotifications.ID_ACK,
                    "PANIC ACKNOWLEDGED", "${verdict.sender.displayName} acknowledged your panic signal."
                )
            }
        }
    }

    private fun fireIntrusion(context: Context, sender: Contact, payload: PanicPayload.Panic, delayMinutes: Long) {
        val intrusionIntent = IntrusionActivity.createIntent(
            context = context,
            senderName = sender.displayName,
            contactUuid = sender.uuid,
            messageId = payload.id,
            sentAt = payload.sentAt,
            delayMinutes = delayMinutes,
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
        context.getSystemService(NotificationManager::class.java).notify(ImpartNotifications.ID_PANIC, notification)
    }

    private fun status(context: Context, id: Int, title: String, text: String) =
        ImpartNotifications.notifyHeadsUp(context, ImpartNotifications.CHANNEL_STATUS, id, title, text)

    private fun security(context: Context, title: String, text: String) =
        status(context, ImpartNotifications.ID_SECURITY, title, text)

    private fun kind(payload: PanicPayload): String = if (payload is PanicPayload.Ack) "An acknowledgement" else "A panic"

    private fun format(epochMillis: Long): String =
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMillis))

    private companion object {
        const val TAG = "ImpartSmsReceiver"
        const val WAKE_LOCK_TAG = "Impart::SmsWakeLock"
        const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
