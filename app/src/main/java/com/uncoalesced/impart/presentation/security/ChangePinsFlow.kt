// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.security

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.uncoalesced.impart.core.security.UnlockResult

// Standard PIN first, then the new pair. Shared by the dashboard lock icon and Settings.
// A duress PIN entered here wipes the node like anywhere else, and nothing announces it.
@Composable
fun ChangePinsFlow(
    shufflePad: Boolean,
    onVerify: (CharArray) -> UnlockResult,
    onSave: (standardPin: CharArray, duressPin: CharArray) -> Result<Unit>,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var verified by remember { mutableStateOf(false) }

    if (!verified) {
        SecurityAuthDialog(
            shufflePad = shufflePad,
            onDismiss = onFinished,
            onPinSubmit = { pin ->
                when (val result = onVerify(pin)) {
                    UnlockResult.Unlocked -> verified = true
                    is UnlockResult.Invalid -> {
                        Toast.makeText(context, "Invalid PIN", Toast.LENGTH_SHORT).show()
                        onFinished()
                    }
                    is UnlockResult.LockedOut -> {
                        val seconds = ((result.remainingLockoutMs + 999L) / 1000L).coerceAtLeast(1L)
                        Toast.makeText(context, "Too many wrong attempts. Try again in ${seconds}s.", Toast.LENGTH_SHORT).show()
                        onFinished()
                    }
                    UnlockResult.Wiped -> onFinished()
                }
            }
        )
    } else {
        PinSetupDialog(
            shufflePad = shufflePad,
            onDismiss = onFinished,
            onSave = { standard, duress ->
                onSave(standard, duress).onSuccess {
                    Toast.makeText(context, "PINs updated", Toast.LENGTH_SHORT).show()
                    onFinished()
                }
            }
        )
    }
}
