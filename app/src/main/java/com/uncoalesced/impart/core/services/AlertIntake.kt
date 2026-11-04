// Engineered by uncoalesced
package com.uncoalesced.impart.core.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.uncoalesced.impart.core.security.AlertRecognizer
import com.uncoalesced.impart.core.security.AlertVerdict
import com.uncoalesced.impart.core.security.AlertVerifier
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.security.Recognition
import com.uncoalesced.impart.core.util.Redact
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.model.displayName
import com.uncoalesced.impart.presentation.intrusion.IntrusionActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// The intrusion pipeline from "this text might be an alert" to something the user sees.
// Shared by ImpartSmsReceiver (SMS_RECEIVED, when Impart is not the default SMS app) and
// SmsDeliverReceiver (SMS_DELIVER, when it is). AlertVerifier decides; this class makes
// every verdict visible. Only an authenticated, fresh, first-seen panic from a trusted
// contact raises the alarm, and nothing else is ever silent.
@Singleton
class AlertIntake @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alertRecognizer: AlertRecognizer,
    private val alertVerifier: AlertVerifier,
    private val securePrefsManager: SecurePrefsManager,
    private val duressManager: DuressManager
) {
    // False when the node was wiped: the receiver must stay dark.
    val active: Boolean get() = !duressManager.isCorrupted.value

    suspend fun handle(body: String) {
        try {
            when (val recognition = alertRecognizer.recognize(body)) {
                Recognition.NotAnAlert -> Unit
                Recognition.OutdatedPrefix -> handle(AlertVerdict.Outdated)
                is Recognition.Alert -> handle(
                    alertVerifier.verify(recognition.ciphertextBase64, securePrefsManager.localUuid(), recognition.tag)
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Intrusion pipeline failed", t)
            status(
                ImpartNotifications.ID_UNREADABLE, "ALERT PIPELINE FAILURE",
                "An Impart alert arrived but could not be processed: ${t.message ?: t.javaClass.simpleName}"
            )
        }
    }

    private fun handle(verdict: AlertVerdict) {
        Log.i(TAG, "Alert verdict: ${verdict.javaClass.simpleName}")
        when (verdict) {
            AlertVerdict.Unreadable -> status(
                ImpartNotifications.ID_UNREADABLE, "UNREADABLE ALERT RECEIVED",
                "An Impart alert arrived that this node could not decrypt. It was not encrypted to this node's key, or it was damaged in transit."
            )
            AlertVerdict.Malformed -> status(
                ImpartNotifications.ID_UNREADABLE, "MALFORMED ALERT RECEIVED",
                "An Impart alert decrypted but its contents were not understood. No alarm was raised."
            )
            AlertVerdict.Outdated -> status(
                ImpartNotifications.ID_UNREADABLE, "ALERT FROM AN OUTDATED VERSION",
                "An Impart alert arrived in an old wire format. Update Impart on both phones and pair again."
            )
            AlertVerdict.UnknownSender -> status(
                ImpartNotifications.ID_UNKNOWN_SENDER, "ALERT FROM UNRECOGNIZED NODE",
                "A node that is not in your Contact Matrix sent an alert. Pair it by QR handshake before it can raise the alarm."
            )
            is AlertVerdict.BadSignature -> security(
                "ALERT FAILED VERIFICATION",
                "An alert claiming to be from ${verdict.claimedSender.displayName} failed its signature check. Someone may be impersonating them. No alarm was raised."
            )
            is AlertVerdict.TagMismatch -> security(
                "ALERT WITH A MISMATCHED TAG",
                "An alert signed by ${verdict.sender.displayName} arrived under another pair's tag. It may have been relayed or tampered with. No alarm was raised."
            )
            is AlertVerdict.NotForThisNode -> security(
                "MISDIRECTED ALERT",
                "${verdict.sender.displayName} sent an alert addressed to a different node. It was not raised."
            )
            is AlertVerdict.FromFuture -> security(
                "ALERT WITH AN IMPOSSIBLE TIME",
                "An alert from ${verdict.sender.displayName} is dated ${format(verdict.sentAt)}, which is in the future. Check this phone's clock. No alarm was raised."
            )
            is AlertVerdict.Stale -> status(
                ImpartNotifications.ID_STALE, "OLD ALERT NOT RAISED",
                "${kind(verdict.payload)} from ${verdict.sender.displayName} sent on ${format(verdict.payload.sentAt)} arrived more than a day late and was not raised."
            )
            is AlertVerdict.Replayed -> security(
                "REPEATED ALERT IGNORED",
                "An alert from ${verdict.sender.displayName} that was already handled arrived again. It may have been copied and re-sent. No alarm was raised."
            )
            is AlertVerdict.Accepted -> when (val payload = verdict.payload) {
                is PanicPayload.Panic -> {
                    Log.i(TAG, "Panic from trusted node ${Redact.uuid(verdict.sender.uuid)}; firing intrusion")
                    fireIntrusion(verdict.sender, payload, verdict.delayMinutes)
                }
                is PanicPayload.Ack -> ImpartNotifications.notifyHeadsUp(
                    context, ImpartNotifications.CHANNEL_ACK, ImpartNotifications.ID_ACK,
                    "PANIC ACKNOWLEDGED", "${verdict.sender.displayName} acknowledged your panic signal."
                )
            }
        }
    }

    private fun fireIntrusion(sender: Contact, payload: PanicPayload.Panic, delayMinutes: Long) {
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

    private fun status(id: Int, title: String, text: String) =
        ImpartNotifications.notifyHeadsUp(context, ImpartNotifications.CHANNEL_STATUS, id, title, text)

    private fun security(title: String, text: String) = status(ImpartNotifications.ID_SECURITY, title, text)

    private fun kind(payload: PanicPayload): String = if (payload is PanicPayload.Ack) "An acknowledgement" else "A panic"

    private fun format(epochMillis: Long): String =
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMillis))

    private companion object {
        const val TAG = "AlertIntake"
    }
}
