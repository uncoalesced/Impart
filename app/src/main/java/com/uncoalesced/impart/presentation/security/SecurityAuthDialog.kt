// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.presentation.theme.*

@Composable
fun SecurityAuthDialog(
    onDismiss: () -> Unit,
    onPinSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
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

                PinField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = "PIN Code"
                )

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
                            if (pin.isNotBlank()) onPinSubmit(pin) else errorMessage = "PIN cannot be empty"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImpartPanicRed,
                            contentColor = ImpartTextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("AUTHENTICATE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PinField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (candidate.length <= DuressManager.MAX_PIN_LENGTH && candidate.all { it.isDigit() }) {
                onValueChange(candidate)
            }
        },
        label = { Text(label, color = ImpartSecondary) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ImpartPanicRed,
            unfocusedBorderColor = ImpartSecondary,
            focusedTextColor = ImpartTextPrimary,
            unfocusedTextColor = ImpartTextPrimary,
            cursorColor = ImpartPanicRed
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
