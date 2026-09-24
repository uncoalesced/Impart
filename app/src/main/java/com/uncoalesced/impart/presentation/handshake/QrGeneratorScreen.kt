// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.handshake

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.uncoalesced.impart.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val QR_SIZE_PX = 512

@Composable
fun QrGeneratorScreen(
    qrState: QrState,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartGround)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "NODE HANDSHAKE",
            color = ImpartSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "SCAN TO CONNECT",
            color = ImpartTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // This node's own number is what the QR advertises so a paired node knows where to
        // send the SMS panic. It cannot be read reliably from the SIM, so it is entered here.
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = { Text("MY PHONE NUMBER (WITH COUNTRY CODE)") },
            placeholder = { Text("+15551234567", color = ImpartSecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ImpartTextPrimary,
                unfocusedTextColor = ImpartTextPrimary,
                focusedBorderColor = ImpartPanicRed,
                unfocusedBorderColor = ImpartSecondary,
                focusedLabelColor = ImpartPanicRed,
                unfocusedLabelColor = ImpartSecondary,
                cursorColor = ImpartPanicRed
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ImpartSecondary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier.padding(24.dp).size(250.dp),
                contentAlignment = Alignment.Center
            ) {
                when (qrState) {
                    QrState.Generating -> CircularProgressIndicator(color = ImpartPanicRed)
                    is QrState.Unavailable -> Text(
                        text = qrState.message,
                        color = ImpartPanicRed,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    is QrState.Ready -> QrImage(payloadJson = qrState.payloadJson)
                }
            }
        }

        if (qrState is QrState.Ready) {
            Text(
                text = "KEY FINGERPRINT",
                color = ImpartSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = qrState.fingerprint,
                color = ImpartTextPrimary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "When pairing, read this aloud. The other phone must show the same fingerprint for you in its Contacts tab.",
                color = ImpartSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Pairing is two-way. After they scan you, scan them.",
            color = ImpartSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QrImage(payloadJson: String) {
    val foreground = ImpartBackground.toArgb()
    val background = ImpartTextPrimary.toArgb()
    // Rendered once per payload, off the main thread. Typing a phone number must not stall
    // the keyboard on a 512x512 pixel loop.
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = payloadJson) {
        value = withContext(Dispatchers.Default) { renderQr(payloadJson, foreground, background) }
    }
    val current = bitmap
    if (current == null) {
        CircularProgressIndicator(color = ImpartPanicRed)
    } else {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = "Handshake QR code",
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun renderQr(content: String, foreground: Int, background: Int): Bitmap? = try {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            pixels[row + x] = if (matrix.get(x, y)) foreground else background
        }
    }
    Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also {
        it.setPixels(pixels, 0, width, 0, 0, width, height)
    }
} catch (e: Exception) {
    null
}
