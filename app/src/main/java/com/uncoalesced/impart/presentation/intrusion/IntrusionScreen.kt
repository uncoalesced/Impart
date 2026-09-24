// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.intrusion

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.impart.presentation.theme.ImpartBackground
import com.uncoalesced.impart.presentation.theme.ImpartPanicRed
import com.uncoalesced.impart.presentation.theme.ImpartTextPrimary

// Background is always the panic red. That color means one thing in this app.
@Composable
fun IntrusionScreen(
    senderName: String,
    sentLabel: String?,
    lat: Double?,
    lng: Double?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartPanicRed)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "CRITICAL EMERGENCY",
            style = MaterialTheme.typography.displaySmall,
            color = ImpartTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "INCOMING PANIC SIGNAL DETECTED",
            style = MaterialTheme.typography.titleLarge,
            color = ImpartTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "FROM: $senderName",
            color = ImpartTextPrimary,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        if (sentLabel != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = sentLabel,
                color = ImpartTextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (lat != null && lng != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ImpartBackground.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TARGET COORDINATES",
                    color = ImpartTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$lat, $lng",
                    color = ImpartTextPrimary,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No maps application installed on this node.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImpartTextPrimary,
                        contentColor = ImpartBackground
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("NAVIGATE (OPEN MAPS)", fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpartBackground,
                contentColor = ImpartTextPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text("ACKNOWLEDGE & DISMISS", style = MaterialTheme.typography.titleMedium)
        }
    }
}
