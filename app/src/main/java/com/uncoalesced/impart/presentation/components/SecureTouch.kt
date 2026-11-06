// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import com.uncoalesced.impart.core.security.ObscuredTouchPolicy

// Put this before clickable or pointerInput on any control that must not be driven through
// an overlay: the PIN pad, the panic buttons, acknowledge, accept-new-keys and revoke. A
// refused touch is announced, so a user under an overlay attack sees why nothing happens.
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.rejectObscuredTouches(onRejected: () -> Unit): Modifier = pointerInteropFilter { event ->
    val reject = ObscuredTouchPolicy.shouldReject(event.flags)
    if (reject && event.actionMasked == android.view.MotionEvent.ACTION_DOWN) onRejected()
    reject
}

@Composable
fun rememberObscuredTouchNotifier(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            Toast.makeText(
                context,
                "Touch blocked: another app is drawing over Impart. Close it and try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
