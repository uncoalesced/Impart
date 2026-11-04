// Engineered by uncoalesced
package com.uncoalesced.impart.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import com.uncoalesced.impart.core.services.AlertIntake
import com.uncoalesced.impart.core.services.ImpartNotifications
import com.uncoalesced.impart.core.util.Redact
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// SMS_DELIVER: only the default SMS app receives it, and nothing else sees the text unless
// this app writes it to the provider. An alert goes to the verifier and is never written,
// so no inbox copy exists on this phone (T14, A4-1). Anything else is stored and announced
// like a normal SMS app would. Protected by BROADCAST_SMS in the manifest.
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {

    @Inject
    lateinit var intake: AlertIntake

    @Inject
    lateinit var dispatcher: SmsDeliverDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val address = messages.first().displayOriginatingAddress.orEmpty()
        val receivedAt = System.currentTimeMillis()
        val subscriptionId = intent.getIntExtra("subscription", -1)

        val appContext = context.applicationContext
        val wakeLock = appContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val outcome = dispatcher.dispatch(address, body, receivedAt, subscriptionId) { alertBody ->
                    if (intake.active) intake.handle(alertBody) else Log.w(TAG, "Alert ignored: this node has been wiped")
                }
                if (outcome is SmsDeliverDispatcher.Outcome.Stored) {
                    ImpartNotifications.notifyMessage(appContext, outcome.address)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Delivery failed for a text from ${Redact.phone(address)}", t)
                ImpartNotifications.notifyHeadsUp(
                    appContext, ImpartNotifications.CHANNEL_STATUS, ImpartNotifications.ID_SMS_STORE_FAILED,
                    "TEXT COULD NOT BE SAVED", "A text message arrived but Impart could not store it: ${t.message ?: t.javaClass.simpleName}"
                )
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
        const val WAKE_LOCK_TAG = "Impart::SmsDeliverWakeLock"
        const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
