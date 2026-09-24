// Engineered by uncoalesced
package com.uncoalesced.impart.core.services

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

// SmsManager reports success the moment a message is queued. The only way to learn that a
// part never left the radio is this sent-intent callback, so a failure here becomes a
// notification rather than a log line nobody reads.
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return
        val target = intent.getStringExtra(EXTRA_TARGET).orEmpty()
        val part = intent.getIntExtra(EXTRA_PART_INDEX, 0) + 1
        val partCount = intent.getIntExtra(EXTRA_PART_COUNT, 1)

        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "SMS part $part/$partCount to $target left the radio")
            return
        }

        val reason = describe(resultCode)
        Log.e(TAG, "SMS part $part/$partCount to $target failed: $reason")
        ImpartNotifications.notifyHeadsUp(
            context,
            ImpartNotifications.CHANNEL_STATUS,
            ImpartNotifications.ID_SMS_SEND_FAILED,
            "PANIC SMS FAILED TO SEND",
            "Part $part of $partCount to $target was not sent: $reason. Check SIM, signal and airplane mode, then hold to fire again."
        )
    }

    private fun describe(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic radio failure"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio is off (airplane mode?)"
        SmsManager.RESULT_ERROR_NULL_PDU -> "empty message"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no cellular service"
        else -> "result code $code"
    }

    companion object {
        private const val TAG = "SmsSentReceiver"
        const val ACTION_SMS_SENT = "com.uncoalesced.impart.SMS_SENT"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_PART_INDEX = "partIndex"
        private const val EXTRA_PART_COUNT = "partCount"
        private val requestCodes = AtomicInteger(1)

        fun pendingIntent(context: Context, target: String, partIndex: Int, partCount: Int): PendingIntent {
            val intent = Intent(context, SmsSentReceiver::class.java)
                .setAction(ACTION_SMS_SENT)
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_PART_INDEX, partIndex)
                .putExtra(EXTRA_PART_COUNT, partCount)
            return PendingIntent.getBroadcast(
                context,
                requestCodes.getAndIncrement(),
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
