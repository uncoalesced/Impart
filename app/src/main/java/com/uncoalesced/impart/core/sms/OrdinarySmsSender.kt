// Engineered by uncoalesced
package com.uncoalesced.impart.core.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.uncoalesced.impart.domain.repository.SmsStore
import com.uncoalesced.impart.domain.repository.WallClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// Sends an ordinary text and records it in the sent box, as a default SMS app must. Alerts
// never come through here: RelayManagerImpl sends them itself and records nothing.
class OrdinarySmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SmsStore,
    private val clock: WallClock
) {
    suspend fun send(address: String, text: String): Result<Unit> = runCatching {
        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            throw IllegalStateException("No SIM is selected for sending SMS.")
        }
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
        val parts = smsManager.divideMessage(text)
        smsManager.sendMultipartTextMessage(address, null, parts, null, null)
        store.storeOutgoing(address, text, clock.now(), subscriptionId)
    }
}
