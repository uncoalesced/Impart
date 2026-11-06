// Engineered by uncoalesced
package com.uncoalesced.impart.core.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.uncoalesced.impart.R
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
            post(AlertNotices.pipelineFailure(t))
        }
    }

    private fun handle(verdict: AlertVerdict) {
        Log.i(TAG, "Alert verdict: ${verdict.javaClass.simpleName}")
        post(AlertNotices.forVerdict(verdict, ::format))
    }

    private fun post(notice: AlertNotice) {
        when (notice) {
            is AlertNotice.Status ->
                ImpartNotifications.notifyHeadsUp(context, ImpartNotifications.CHANNEL_STATUS, notice.id, notice.title, notice.text)
            is AlertNotice.Acknowledged ->
                ImpartNotifications.notifyHeadsUp(context, ImpartNotifications.CHANNEL_ACK, ImpartNotifications.ID_ACK, notice.title, notice.text)
            is AlertNotice.Alarm -> {
                Log.i(TAG, "Panic from trusted node ${Redact.uuid(notice.sender.uuid)}; firing intrusion")
                fireIntrusion(notice.sender, notice.payload, notice.delayMinutes)
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
            .setSmallIcon(R.drawable.ic_stat_impart)
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

    private fun format(epochMillis: Long): String =
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMillis))

    private companion object {
        const val TAG = "AlertIntake"
    }
}
