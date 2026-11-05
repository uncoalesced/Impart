// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.security.PinEntry
import com.uncoalesced.impart.presentation.components.PinPad
import com.uncoalesced.impart.presentation.components.SecureDialog
import com.uncoalesced.impart.presentation.theme.ImpartWarning
import com.uncoalesced.impart.presentation.theme.ImpartSurfaceRaised
import com.uncoalesced.impart.presentation.theme.ImpartTextMuted
import com.uncoalesced.impart.presentation.theme.ImpartTextPrimary

private enum class SetupStep(val title: String) {
    STANDARD("New standard PIN"),
    STANDARD_CONFIRM("Confirm standard PIN"),
    DURESS("New duress PIN"),
    DURESS_CONFIRM("Confirm duress PIN")
}

// Two uses: changing PINs after the standard PIN was entered, and the mandatory first-run
// setup (required = true), which cannot be dismissed. Four entries on the in-app pad, one
// step at a time. onSave returns the manager's verdict so the dialog can show exactly why a
// pair of PINs was refused. Every buffer is zeroed when the dialog leaves the screen.
@Composable
fun PinSetupDialog(
    shufflePad: Boolean,
    onDismiss: () -> Unit,
    onSave: (standardPin: CharArray, duressPin: CharArray) -> Result<Unit>,
    required: Boolean = false
) {
    val entries = remember { SetupStep.entries.associateWith { PinEntry() } }
    val current = remember { PinEntry() }
    var step by remember { mutableStateOf(SetupStep.STANDARD) }
    var digitCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            current.clear()
            entries.values.forEach { it.clear() }
        }
    }

    fun restart(message: String?) {
        entries.values.forEach { it.clear() }
        current.clear()
        digitCount = 0
        step = SetupStep.STANDARD
        errorMessage = message
    }

    fun commit() {
        val standard = entries.getValue(SetupStep.STANDARD).snapshot()
        val duress = entries.getValue(SetupStep.DURESS).snapshot()
        try {
            val verdict = onSave(standard, duress)
            verdict.exceptionOrNull()?.let { restart(it.message) }
        } finally {
            standard.fill('\u0000')
            duress.fill('\u0000')
        }
    }

    fun advance() {
        val typed = current.snapshot()
        try {
            if (typed.size < DuressManager.MIN_PIN_LENGTH) {
                errorMessage = "PINs must be ${DuressManager.MIN_PIN_LENGTH} to ${DuressManager.MAX_PIN_LENGTH} digits."
                return
            }
            val target = entries.getValue(step)
            target.clear()
            typed.forEach { target.push(it) }
        } finally {
            typed.fill('\u0000')
        }
        current.clear()
        digitCount = 0
        errorMessage = null
        when (step) {
            SetupStep.STANDARD -> step = SetupStep.STANDARD_CONFIRM
            SetupStep.STANDARD_CONFIRM -> {
                if (!entries.getValue(SetupStep.STANDARD).contentEquals(entries.getValue(SetupStep.STANDARD_CONFIRM))) {
                    restart("Standard PINs do not match.")
                } else {
                    step = SetupStep.DURESS
                }
            }
            SetupStep.DURESS -> step = SetupStep.DURESS_CONFIRM
            SetupStep.DURESS_CONFIRM -> {
                if (!entries.getValue(SetupStep.DURESS).contentEquals(entries.getValue(SetupStep.DURESS_CONFIRM))) {
                    restart("Duress PINs do not match.")
                } else {
                    commit()
                }
            }
        }
    }

    SecureDialog(
        onDismissRequest = { if (!required) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !required, dismissOnClickOutside = !required)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ImpartSurfaceRaised),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (required) "CHOOSE YOUR PINS" else "SET NODE PINS",
                    color = ImpartTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The standard PIN unlocks Impart. The duress PIN, entered anywhere Impart asks for a PIN, silently wipes the node and shows a fake error screen. ${DuressManager.MIN_PIN_LENGTH} to ${DuressManager.MAX_PIN_LENGTH} digits each, and they must differ.",
                    color = ImpartTextMuted,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "STEP ${step.ordinal + 1} OF ${SetupStep.entries.size}: ${step.title.uppercase()}",
                    color = ImpartTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                PinPad(
                    entry = current,
                    digitCount = digitCount,
                    onChanged = { digitCount = current.length },
                    shuffled = shufflePad,
                    submitLabel = "NEXT",
                    onSubmit = { advance() }
                )

                val error = errorMessage
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error, color = ImpartWarning, fontSize = 12.sp)
                }

                if (!required) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        TextButton(onClick = onDismiss) {
                            Text("CANCEL", color = ImpartTextMuted)
                        }
                    }
                }
            }
        }
    }
}
