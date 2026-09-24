// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.presentation.theme.*

// Two uses: changing PINs after the standard PIN was entered, and the mandatory first-run
// setup (required = true), which cannot be dismissed. onSave returns the manager's verdict
// so the dialog can show exactly why a pair of PINs was refused.
@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (standardPin: String, duressPin: String) -> Result<Unit>,
    required: Boolean = false
) {
    var standardPin by remember { mutableStateOf("") }
    var standardConfirm by remember { mutableStateOf("") }
    var duressPin by remember { mutableStateOf("") }
    var duressConfirm by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!required) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !required, dismissOnClickOutside = !required)
    ) {
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
                    color = ImpartSecondary,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                PinField(value = standardPin, onValueChange = { standardPin = it }, label = "New standard PIN")
                Spacer(modifier = Modifier.height(8.dp))
                PinField(value = standardConfirm, onValueChange = { standardConfirm = it }, label = "Confirm standard PIN")
                Spacer(modifier = Modifier.height(16.dp))
                PinField(value = duressPin, onValueChange = { duressPin = it }, label = "New duress PIN")
                Spacer(modifier = Modifier.height(8.dp))
                PinField(value = duressConfirm, onValueChange = { duressConfirm = it }, label = "Confirm duress PIN")

                val error = errorMessage
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error, color = ImpartPanicRed, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (required) Arrangement.End else Arrangement.SpaceBetween
                ) {
                    if (!required) {
                        TextButton(onClick = onDismiss) {
                            Text("CANCEL", color = ImpartSecondary)
                        }
                    }
                    Button(
                        onClick = {
                            errorMessage = when {
                                standardPin != standardConfirm -> "Standard PINs do not match."
                                duressPin != duressConfirm -> "Duress PINs do not match."
                                else -> onSave(standardPin, duressPin).exceptionOrNull()?.message
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImpartPanicRed,
                            contentColor = ImpartTextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SAVE PINS", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
