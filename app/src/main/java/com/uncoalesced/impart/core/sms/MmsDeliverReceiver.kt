// Engineered by uncoalesced
package com.uncoalesced.impart.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.uncoalesced.impart.core.services.ImpartNotifications
import com.uncoalesced.impart.domain.repository.SmsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// WAP_PUSH_DELIVER for application/vnd.wap.mms-message: the role requires a receiver for it.
// Impart does not download MMS. It records "MMS received" with the sender, when the
// notification PDU names one, and the time, so the user knows to look elsewhere.
@AndroidEntryPoint
class MmsDeliverReceiver : BroadcastReceiver() {

    @Inject
    lateinit var store: SmsStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val sender = MmsNotificationParser.senderOf(intent.getByteArrayExtra("data")) ?: UNKNOWN_SENDER
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                store.storeIncoming(sender, MMS_NOTICE, System.currentTimeMillis(), intent.getIntExtra("subscription", -1))
                ImpartNotifications.notifyMessage(appContext, sender)
            } catch (t: Throwable) {
                Log.e(TAG, "Could not record an MMS notice", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MmsDeliverReceiver"
        const val MMS_NOTICE = "MMS received. Impart does not download picture messages; use another app to read it."
        const val UNKNOWN_SENDER = "unknown"
    }
}
