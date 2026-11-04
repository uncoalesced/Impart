// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.impart.core.security.PinEntry
import com.uncoalesced.impart.presentation.components.PinPad
import com.uncoalesced.impart.presentation.components.SecureDialog
import com.uncoalesced.impart.presentation.theme.ImpartBackground
import com.uncoalesced.impart.presentation.theme.ImpartSecondary
import com.uncoalesced.impart.presentation.theme.ImpartTextPrimary

// Asks for a PIN before a sensitive change. The caller receives the digits as a CharArray
// and must not keep them; the array is zeroed once the callback returns.
@Composable
fun SecurityAuthDialog(
    shufflePad: Boolean,
    onDismiss: () -> Unit,
    onPinSubmit: (CharArray) -> Unit
) {
    val entry = remember { PinEntry() }
    var digitCount by remember { mutableIntStateOf(0) }
    DisposableEffect(entry) { onDispose { entry.clear() } }

    SecureDialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ImpartBackground),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SECURITY AUTHENTICATION",
                    color = ImpartTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ENTER NODE ACCESS OR DURESS PIN",
                    color = ImpartSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(24.dp))

                PinPad(
                    entry = entry,
                    digitCount = digitCount,
                    onChanged = { digitCount = entry.length },
                    shuffled = shufflePad,
                    submitEnabled = digitCount > 0,
                    onSubmit = {
                        val entered = entry.snapshot()
                        entry.clear()
                        digitCount = 0
                        try {
                            onPinSubmit(entered)
                        } finally {
                            entered.fill('\u0000')
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = ImpartSecondary)
                    }
                }
            }
        }
    }
}
