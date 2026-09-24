// Engineered by uncoalesced
package com.uncoalesced.impart.core.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.util.AudioCaptureHelper
import com.uncoalesced.impart.presentation.intrusion.IntrusionActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class ImpartSmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var cryptoManager: CryptoManager

    @Inject
    lateinit var powerManager: PowerManager

    @Inject
    lateinit var audioCaptureHelper: AudioCaptureHelper

    companion object {
        private const val CHANNEL_PANIC = "impart_panic_channel"
        private const val CHANNEL_ACK = "impart_ack_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        
        val fullMessageBody = StringBuilder()
        for (msg in messages) {
            fullMessageBody.append(msg.messageBody)
        }

        val bodyStr = fullMessageBody.toString()
        if (bodyStr.startsWith("IMPART_ALERT:")) {
            try {
                abortBroadcast()
            } catch (e: Exception) {
                // Ignore if not default SMS app on modern Android
            }

            val ciphertext = bodyStr.removePrefix("IMPART_ALERT:")

            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Impart::SmsWakeLock"
            ).apply { acquire(30_000L) }

            try {
                val plaintext = cryptoManager.decrypt(ciphertext)
                val json = JSONObject(plaintext)
                val type = json.optString("type", "PANIC")
                
                if (type == "PANIC") {
                    val senderPhoneNumber = json.optString("senderPhoneNumber")
                    val senderPublicKey = json.optString("senderPublicKey")
                    val lat = if (json.has("lat")) json.getDouble("lat") else null
                    val lng = if (json.has("lng")) json.getDouble("lng") else null

                    CoroutineScope(Dispatchers.IO).launch {
                        audioCaptureHelper.captureAndEncrypt(10_000L)
                    }

                    fireIntrusionIntent(context, senderPhoneNumber, senderPublicKey, lat, lng)
                } else if (type == "ACK") {
                    fireAckNotification(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }
    }

    private fun fireIntrusionIntent(
        context: Context,
        senderPhoneNumber: String,
        senderPublicKey: String,
        lat: Double?,
        lng: Double?
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_PANIC,
                "Panic Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intrusionIntent = Intent(context, IntrusionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("senderPhoneNumber", senderPhoneNumber)
            putExtra("senderPublicKey", senderPublicKey)
            if (lat != null && lng != null) {
                putExtra("lat", lat)
                putExtra("lng", lng)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intrusionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PANIC)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("IMPART — PANIC (OFF-GRID)")
            .setContentText("Incoming Panic Signal Detected via SMS Fallback")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun fireAckNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ACK,
                "Acknowledgment Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ACK)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("IMPART — ACKNOWLEDGED (OFF-GRID)")
            .setContentText("Your panic signal was acknowledged via SMS Fallback.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
