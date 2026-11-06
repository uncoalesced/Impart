// Engineered by uncoalesced
package com.uncoalesced.impart.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import com.uncoalesced.impart.core.security.AlertWire
import com.uncoalesced.impart.core.security.LEGACY_ALERT_PREFIX
import com.uncoalesced.impart.core.sms.DefaultSmsRole
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// The wake path when Impart is not the default SMS app. An incoming SMS wakes this receiver
// even from Doze; everything after the syntactic filter runs off the main thread under a
// wake lock, in AlertIntake. When Impart is the default SMS app the same text arrives as
// SMS_DELIVER in SmsDeliverReceiver, so this receiver steps aside to handle nothing twice.
@AndroidEntryPoint
class ImpartSmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var intake: AlertIntake

    @Inject
    lateinit var defaultSmsRole: DefaultSmsRole

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (defaultSmsRole.isDefault()) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        // Syntactic filter only: an ordinary text never costs a wake lock or a database read.
        if (!body.startsWith(LEGACY_ALERT_PREFIX) && !AlertWire.couldBeAlert(body)) return

        if (!intake.active) {
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
                intake.handle(body)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ImpartSmsReceiver"
        const val WAKE_LOCK_TAG = "Impart::SmsWakeLock"
        const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
