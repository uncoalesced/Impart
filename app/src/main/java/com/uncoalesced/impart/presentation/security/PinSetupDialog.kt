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
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.presentation.theme.*

// Reached only after the standard PIN was entered correctly. onSave returns the manager's
// verdict so the dialog can show exactly why a pair of PINs was refused.
@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (standardPin: String, duressPin: String) -> Result<Unit>
) {
    var standardPin by remember { mutableStateOf("") }
    var standardConfirm by remember { mutableStateOf("") }
    var duressPin by remember { mutableStateOf("") }
    var duressConfirm by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
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
                    text = "SET NODE PINS",
                    color = ImpartTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The standard PIN opens this screen. The duress PIN wipes the node and shows the decoy. ${DuressManager.MIN_PIN_LENGTH} to ${DuressManager.MAX_PIN_LENGTH} digits each, and they must differ.",
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = ImpartSecondary)
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
