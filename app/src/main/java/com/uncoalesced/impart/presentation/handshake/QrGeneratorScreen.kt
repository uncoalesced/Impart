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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.uncoalesced.impart.presentation.theme.*

@Composable
fun QrGeneratorScreen(
    payloadJson: String,
    errorMessage: String? = null,
    phoneNumber: String = "",
    onPhoneNumberChange: (String) -> Unit = {}
) {
    val bitmap = if (errorMessage == null) generateQrCode(payloadJson) else null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartBackground)
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
            label = { Text("MY PHONE NUMBER") },
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
            Box(modifier = Modifier.padding(24.dp)) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(250.dp)
                    )
                } else {
                    Text(
                        text = errorMessage ?: "FAILED TO GENERATE",
                        color = ImpartPanicRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun generateQrCode(content: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        val fgColor = android.graphics.Color.parseColor("#2B2D42") // ImpartBackground
        val bgColor = android.graphics.Color.parseColor("#EDF2F4") // ImpartTextPrimary

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) fgColor else bgColor)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
