// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.impart.core.security.UnlockResult
import com.uncoalesced.impart.presentation.theme.*
import kotlinx.coroutines.delay

// The PIN gate in front of the app. The decision about what a PIN means lives in
// AppLockManager and DuressManager; this screen only collects digits and shows the answer.
// A duress PIN produces no message here: the whole window turns into the decoy.
@Composable
fun AppLockScreen(onSubmit: (String) -> UnlockResult) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lockedUntil) {
        while (lockedUntil > now) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val waitSeconds = ((lockedUntil - now) / 1000).coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartGround)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = ImpartSecondary, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "IMPART LOCKED", color = ImpartTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "ENTER YOUR PIN", color = ImpartSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(24.dp))

        PinField(value = pin, onValueChange = { pin = it }, label = "PIN")

        val current = message
        if (waitSeconds > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Too many wrong attempts. Try again in ${waitSeconds}s.",
                color = ImpartAmber,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        } else if (current != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = current, color = ImpartAmber, fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val entered = pin
                pin = ""
                now = System.currentTimeMillis()
                when (val result = onSubmit(entered)) {
                    UnlockResult.Unlocked, UnlockResult.Wiped -> message = null
                    is UnlockResult.Invalid -> {
                        message = "Wrong PIN."
                        lockedUntil = result.lockedUntil
                    }
                    is UnlockResult.LockedOut -> lockedUntil = result.lockedUntil
                }
            },
            enabled = pin.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = ImpartBackground, contentColor = ImpartTextPrimary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("UNLOCK", fontWeight = FontWeight.Bold)
        }
    }
}
