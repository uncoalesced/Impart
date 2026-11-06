// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.messages

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.uncoalesced.impart.MainActivity
import com.uncoalesced.impart.core.util.WindowHardening

// The default SMS role requires an activity for SENDTO sms:/smsto:/mms:/mmsto:. It shows
// nothing itself: it hands the number to MainActivity, which sits behind the legal gate,
// PIN setup and the lock, and finishes. So it is exported without a permission and still
// exposes nothing.
class ComposeSmsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowHardening.harden(this)
        val address = intent?.data?.schemeSpecificPart.orEmpty().substringBefore('?').trim()
        val body = intent?.getStringExtra("sms_body") ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_COMPOSE_TO, address)
                .putExtra(MainActivity.EXTRA_COMPOSE_BODY, body)
        )
        finish()
    }
}
