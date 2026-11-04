// Engineered by uncoalesced
package com.uncoalesced.impart.core.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

// Single owner of every channel and notification id. Channels are created once in
// ImpartApplication.onCreate, which also runs when an SMS cold-starts the process.
object ImpartNotifications {
    const val CHANNEL_PANIC = "impart_panic_channel"
    const val CHANNEL_ACK = "impart_ack_channel"
    const val CHANNEL_STATUS = "impart_status_channel"
    const val CHANNEL_MESSAGES = "impart_messages_channel"

    const val ID_PANIC = 1001
    const val ID_ACK = 1002
    const val ID_DMS_WARNING = 1003
    const val ID_UNREADABLE = 1004
    const val ID_UNKNOWN_SENDER = 1005
    const val ID_SMS_SEND_FAILED = 1006
    const val ID_DMS_REPORT = 1007
    const val ID_SECURITY = 1008
    const val ID_STALE = 1009
    const val ID_SMS_STORE_FAILED = 1010
    // Ordinary texts in default-SMS mode: one notification per sender, ids from this base.
    const val ID_MESSAGE_BASE = 2000

    private const val LEGACY_CHANNEL_DMS_WARNING = "impart_dms_warning"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // Alarm-stream sound on the channel itself: if Android 14 denies the full-screen
        // intent, the heads-up notification still has to be loud enough to wake someone.
        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val panic = NotificationChannel(CHANNEL_PANIC, "Panic alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Incoming panic signals from trusted nodes"
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAttributes)
        }
        val ack = NotificationChannel(CHANNEL_ACK, "Acknowledgements", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A trusted node acknowledged your panic"
        }
        val status = NotificationChannel(CHANNEL_STATUS, "Node status", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Delivery failures, unreadable alerts and dead man's switch warnings"
        }
        val messages = NotificationChannel(CHANNEL_MESSAGES, "Text messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Ordinary texts, only while Impart is your SMS app"
        }
        manager.createNotificationChannels(listOf(panic, ack, status, messages))
        manager.deleteNotificationChannel(LEGACY_CHANNEL_DMS_WARNING)
    }

    fun notifyHeadsUp(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        text: String
    ) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    // An ordinary text arrived while Impart is the default SMS app. The number is shown, the
    // body is not: the lock screen must not read texts aloud for anyone who picks up the phone.
    fun notifyMessage(context: Context, address: String) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("New text message")
            .setContentText("From $address. Unlock Impart to read it.")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
        if (launch != null) {
            builder.setContentIntent(
                android.app.PendingIntent.getActivity(
                    context, 0, launch,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        context.getSystemService(NotificationManager::class.java)
            .notify(ID_MESSAGE_BASE + (address.hashCode() and 0xFFFF), builder.build())
    }

    fun cancel(context: Context, notificationId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
    }
}
