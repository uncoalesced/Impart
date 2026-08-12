// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.intrusion

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.uncoalesced.impart.domain.repository.RelayManager
import com.uncoalesced.impart.presentation.theme.ImpartTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IntrusionActivity : ComponentActivity() {

    @Inject
    lateinit var relayManager: RelayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setShowWhenLocked/setTurnScreenOn are API 27+; minSdk is 26, so API 26 falls back
        // to the equivalent window flags or the intrusion screen crashes before it renders.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)
        
        val senderFcmToken = intent.getStringExtra("senderFcmToken")
        val senderPublicKey = intent.getStringExtra("senderPublicKey")
        val lat = if (intent.hasExtra("lat")) intent.getDoubleExtra("lat", 0.0) else null
        val lng = if (intent.hasExtra("lng")) intent.getDoubleExtra("lng", 0.0) else null

        setContent {
            ImpartTheme {
                IntrusionScreen(
                    lat = lat,
                    lng = lng,
                    onDismiss = {
                        if (senderFcmToken != null && senderPublicKey != null) {
                            lifecycleScope.launch {
                                relayManager.dispatchAck(senderFcmToken, senderPublicKey)
                                finish()
                            }
                        } else {
                            finish()
                        }
                    }
                )
            }
        }
    }
}
