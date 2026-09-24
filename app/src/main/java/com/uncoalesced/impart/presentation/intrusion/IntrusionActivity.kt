// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.intrusion

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.uncoalesced.impart.core.services.ImpartNotifications
import com.uncoalesced.impart.domain.repository.RelayManager
import com.uncoalesced.impart.presentation.theme.ImpartTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IntrusionDetails(
    val senderName: String,
    val senderPhoneNumber: String?,
    val senderPublicKeyBase64: String?,
    val lat: Double?,
    val lng: Double?
)

@AndroidEntryPoint
class IntrusionActivity : ComponentActivity() {

    @Inject
    lateinit var relayManager: RelayManager

    private lateinit var alarm: IntrusionAlarm
    private val details = mutableStateOf(readDetails(null))
    private var dismissing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setShowWhenLocked/setTurnScreenOn are API 27+; minSdk is 26, so API 26 uses the
        // equivalent window flags or the intrusion screen crashes before it renders.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)

        alarm = IntrusionAlarm(this)
        showAlert(intent)

        setContent {
            ImpartTheme {
                val current by details
                IntrusionScreen(
                    senderName = current.senderName,
                    lat = current.lat,
                    lng = current.lng,
                    onDismiss = ::acknowledgeAndFinish
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dismissing = false
        showAlert(intent)
    }

    override fun onDestroy() {
        alarm.stop()
        super.onDestroy()
    }

    private fun showAlert(intent: Intent?) {
        details.value = readDetails(intent)
        // The channel sound is already ringing from the notification; the looping alarm
        // takes over from here.
        ImpartNotifications.cancel(this, ImpartNotifications.ID_PANIC)
        alarm.start()
    }

    private fun acknowledgeAndFinish() {
        if (dismissing) return
        dismissing = true
        alarm.stop()
        val current = details.value
        val phone = current.senderPhoneNumber
        val key = current.senderPublicKeyBase64
        lifecycleScope.launch {
            if (phone.isNullOrBlank() || key.isNullOrBlank()) {
                Log.w(TAG, "Sender left no return address; no ACK sent")
                Toast.makeText(this@IntrusionActivity, "Sender left no return address. No ACK sent.", Toast.LENGTH_LONG).show()
            } else {
                relayManager.dispatchAck(phone, key).onFailure { error ->
                    Toast.makeText(
                        this@IntrusionActivity,
                        "ACK could not be sent: ${error.message ?: error.javaClass.simpleName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            finish()
        }
    }

    private fun readDetails(intent: Intent?): IntrusionDetails = IntrusionDetails(
        senderName = intent?.getStringExtra(EXTRA_SENDER_NAME) ?: "UNKNOWN NODE",
        senderPhoneNumber = intent?.getStringExtra(EXTRA_SENDER_PHONE),
        senderPublicKeyBase64 = intent?.getStringExtra(EXTRA_SENDER_KEY),
        lat = intent?.takeIf { it.hasExtra(EXTRA_LAT) }?.getDoubleExtra(EXTRA_LAT, 0.0),
        lng = intent?.takeIf { it.hasExtra(EXTRA_LNG) }?.getDoubleExtra(EXTRA_LNG, 0.0)
    )

    companion object {
        private const val TAG = "IntrusionActivity"
        private const val EXTRA_SENDER_NAME = "senderName"
        private const val EXTRA_SENDER_PHONE = "senderPhoneNumber"
        private const val EXTRA_SENDER_KEY = "senderPublicKey"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LNG = "lng"

        fun createIntent(
            context: Context,
            senderName: String,
            senderPhoneNumber: String?,
            senderPublicKeyBase64: String,
            lat: Double?,
            lng: Double?
        ): Intent = Intent(context, IntrusionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_SENDER_PHONE, senderPhoneNumber)
            putExtra(EXTRA_SENDER_KEY, senderPublicKeyBase64)
            if (lat != null && lng != null) {
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
            }
        }
    }
}
