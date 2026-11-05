// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.impart.presentation.theme.ImpartMono
import com.uncoalesced.impart.core.security.PinEntry
import com.uncoalesced.impart.core.security.UnlockResult
import com.uncoalesced.impart.presentation.components.PinPad
import com.uncoalesced.impart.presentation.theme.ImpartWarning
import com.uncoalesced.impart.presentation.theme.ImpartGround
import com.uncoalesced.impart.presentation.theme.ImpartTextMuted
import com.uncoalesced.impart.presentation.theme.ImpartTextPrimary
import kotlinx.coroutines.delay

private fun secondsOf(millis: Long): Long = (millis + 999L) / 1000L

// The PIN gate in front of the app. The decision about what a PIN means lives in
// AppLockManager and DuressManager; this screen only collects digits on the in-app pad and
// shows the answer. A duress PIN produces no message here: the whole window turns into the
// decoy. The digits are handed over as a CharArray and zeroed straight after.
@Composable
fun AppLockScreen(
    shufflePad: Boolean,
    onSubmit: (CharArray) -> UnlockResult
) {
    val entry = remember { PinEntry() }
    var digitCount by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    // Remaining wait as reported by AppLockManager, counted down locally. The manager measures
    // the real wait on the monotonic clock; this is only the display.
    var waitSeconds by remember { mutableLongStateOf(0L) }
    var countdownGeneration by remember { mutableIntStateOf(0) }

    DisposableEffect(entry) { onDispose { entry.clear() } }

    LaunchedEffect(countdownGeneration) {
        while (waitSeconds > 0) {
            delay(1000)
            waitSeconds -= 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartGround)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = ImpartTextMuted, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "IMPART LOCKED", color = ImpartTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "ENTER YOUR PIN", color = ImpartTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(24.dp))

        PinPad(
            entry = entry,
            digitCount = digitCount,
            onChanged = { digitCount = entry.length },
            shuffled = shufflePad,
            submitEnabled = digitCount > 0 && waitSeconds <= 0L,
            submitLabel = "UNLOCK",
            onSubmit = {
                val entered = entry.snapshot()
                entry.clear()
                digitCount = 0
                try {
                    when (val result = onSubmit(entered)) {
                        UnlockResult.Unlocked, UnlockResult.Wiped -> message = null
                        is UnlockResult.Invalid -> {
                            message = "Wrong PIN."
                            waitSeconds = secondsOf(result.remainingLockoutMs)
                            countdownGeneration += 1
                        }
                        is UnlockResult.LockedOut -> {
                            waitSeconds = secondsOf(result.remainingLockoutMs)
                            countdownGeneration += 1
                        }
                    }
                } finally {
                    entered.fill('\u0000')
                }
            }
        )

        val current = message
        if (waitSeconds > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Too many wrong attempts. Try again in ${waitSeconds}s.",
                color = ImpartWarning,
                fontSize = 12.sp,
                fontFamily = ImpartMono,
                textAlign = TextAlign.Center
            )
        } else if (current != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = current, color = ImpartWarning, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}
