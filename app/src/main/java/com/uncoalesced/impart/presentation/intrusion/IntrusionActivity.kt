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
import com.uncoalesced.impart.core.util.WindowHardening
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.domain.repository.RelayManager
import com.uncoalesced.impart.presentation.theme.ImpartTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class IntrusionDetails(
    val senderName: String,
    val contactUuid: String?,
    val messageId: String?,
    val sentAt: Long?,
    val delayMinutes: Long,
    val lat: Double?,
    val lng: Double?
)

// Deliberately not behind the app lock: the alarm and its acknowledgement must work over the
// lock screen, and this activity shows nothing beyond the one alert it was opened for.
@AndroidEntryPoint
class IntrusionActivity : ComponentActivity() {

    @Inject
    lateinit var relayManager: RelayManager

    @Inject
    lateinit var contactRepository: ContactRepository

    private lateinit var alarm: IntrusionAlarm
    private val details = mutableStateOf(readDetails(null))
    private var dismissing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowHardening.harden(this)

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
                    sentLabel = sentLabel(current),
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

    // The ACK goes to the contact as stored at handshake time. Nothing in the alert decides
    // where it is sent.
    private fun acknowledgeAndFinish() {
        if (dismissing) return
        dismissing = true
        alarm.stop()
        val current = details.value
        lifecycleScope.launch {
            val contact = current.contactUuid?.let { contactRepository.findActiveByUuid(it) }
            val messageId = current.messageId
            if (contact == null || messageId == null) {
                Log.w(TAG, "Sender is no longer an active contact; no ACK sent")
                Toast.makeText(this@IntrusionActivity, "Sender is no longer a trusted contact. No ACK sent.", Toast.LENGTH_LONG).show()
            } else {
                relayManager.dispatchAck(contact, messageId).onFailure { error ->
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

    private fun sentLabel(current: IntrusionDetails): String? {
        val sentAt = current.sentAt ?: return null
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sentAt))
        return if (current.delayMinutes >= DELAY_NOTICE_MINUTES) {
            "SENT $time, DELAYED ${current.delayMinutes} MIN"
        } else {
            "SENT $time"
        }
    }

    private fun readDetails(intent: Intent?): IntrusionDetails = IntrusionDetails(
        senderName = intent?.getStringExtra(EXTRA_SENDER_NAME) ?: "UNKNOWN NODE",
        contactUuid = intent?.getStringExtra(EXTRA_CONTACT_UUID),
        messageId = intent?.getStringExtra(EXTRA_MESSAGE_ID),
        sentAt = intent?.takeIf { it.hasExtra(EXTRA_SENT_AT) }?.getLongExtra(EXTRA_SENT_AT, 0L),
        delayMinutes = intent?.getLongExtra(EXTRA_DELAY_MINUTES, 0L) ?: 0L,
        lat = intent?.takeIf { it.hasExtra(EXTRA_LAT) }?.getDoubleExtra(EXTRA_LAT, 0.0),
        lng = intent?.takeIf { it.hasExtra(EXTRA_LNG) }?.getDoubleExtra(EXTRA_LNG, 0.0)
    )

    companion object {
        private const val TAG = "IntrusionActivity"
        private const val DELAY_NOTICE_MINUTES = 2L
        private const val EXTRA_SENDER_NAME = "senderName"
        private const val EXTRA_CONTACT_UUID = "contactUuid"
        private const val EXTRA_MESSAGE_ID = "messageId"
        private const val EXTRA_SENT_AT = "sentAt"
        private const val EXTRA_DELAY_MINUTES = "delayMinutes"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LNG = "lng"

        fun createIntent(
            context: Context,
            senderName: String,
            contactUuid: String,
            messageId: String,
            sentAt: Long,
            delayMinutes: Long,
            lat: Double?,
            lng: Double?
        ): Intent = Intent(context, IntrusionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_CONTACT_UUID, contactUuid)
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_SENT_AT, sentAt)
            putExtra(EXTRA_DELAY_MINUTES, delayMinutes)
            if (lat != null && lng != null) {
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
            }
        }
    }
}
