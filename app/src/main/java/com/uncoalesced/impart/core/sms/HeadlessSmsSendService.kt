// Engineered by uncoalesced
package com.uncoalesced.impart.core.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// RESPOND_VIA_MESSAGE: the dialer's "reply with a text" on an incoming call hands the number
// and the text here. Protected by SEND_RESPOND_VIA_MESSAGE, which only the system holds.
@AndroidEntryPoint
class HeadlessSmsSendService : Service() {

    @Inject
    lateinit var sender: OrdinarySmsSender

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart.orEmpty().substringBefore('?')
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE && address.isNotBlank() && text.isNotBlank()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                sender.send(address, text).onFailure { Log.e(TAG, "Quick reply failed", it) }
                stopSelf(startId)
            }
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "HeadlessSmsSendService"
    }
}
