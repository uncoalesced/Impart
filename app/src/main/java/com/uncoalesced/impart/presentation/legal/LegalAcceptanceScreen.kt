// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.uncoalesced.impart.presentation.components.ImpartLockup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uncoalesced.impart.domain.model.LegalDocument
import com.uncoalesced.impart.presentation.theme.*

// Shown once, before anything else, and again whenever LegalDocument.CURRENT_VERSION moves.
// The card uses amber, not panic red: this is a warning, not an emergency.
@Composable
fun LegalAcceptanceScreen(viewModel: LegalViewModel, onDecline: () -> Unit, showMark: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state != LegalDocumentState.Closed) {
        LegalDocumentScreen(state = state, onBack = viewModel::close, onRetry = viewModel::open)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartGround)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        if (showMark) {
            ImpartLockup(markSize = 64.dp, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(text = "BEFORE YOU START", color = ImpartTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Impart pages people you trust. Read this first.", color = ImpartTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ImpartSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Warning("Impart is not an emergency service. If someone is in danger, call your local emergency number first.")
                Warning("Alerts travel by SMS. Delivery is not guaranteed, and a panic can arrive late or not at all.")
                Warning("Alerts are sent as regular texts. We recommend a plan with unlimited texts. Without one, your carrier may charge you for every alert.")
                Warning("Nothing leaves your phone except encrypted texts to the people you pair with.")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        DocumentLink(LegalDocument.TERMS) { viewModel.open(it) }
        DocumentLink(LegalDocument.PRIVACY) { viewModel.open(it) }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = viewModel::accept,
            colors = ButtonDefaults.buttonColors(containerColor = ImpartTextPrimary, contentColor = ImpartGround),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("I UNDERSTAND AND ACCEPT", fontWeight = FontWeight.ExtraBold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text("DECLINE AND CLOSE", color = ImpartTextMuted)
        }
    }
}

@Composable
private fun Warning(text: String) {
    Row {
        Text(text = "!", color = ImpartWarning, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(16.dp))
        Text(text = text, color = ImpartTextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun DocumentLink(document: LegalDocument, onOpen: (LegalDocument) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(document) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Read the ${document.title}", color = ImpartTextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ImpartTextMuted)
    }
}
