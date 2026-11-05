// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.legal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.impart.domain.model.LegalBlock
import com.uncoalesced.impart.domain.model.LegalDocument
import com.uncoalesced.impart.presentation.theme.*

@Composable
fun LegalDocumentScreen(
    state: LegalDocumentState,
    onBack: () -> Unit,
    onRetry: (LegalDocument) -> Unit
) {
    BackHandler(onBack = onBack)
    val title = when (state) {
        is LegalDocumentState.Loading -> state.document.title
        is LegalDocumentState.Loaded -> state.document.title
        is LegalDocumentState.Failed -> state.document.title
        LegalDocumentState.Closed -> ""
    }

    Column(modifier = Modifier.fillMaxSize().background(ImpartGround)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(ImpartSurface).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ImpartTextPrimary)
            }
            Text(text = title.uppercase(), color = ImpartTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        when (state) {
            is LegalDocumentState.Loading, LegalDocumentState.Closed -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ImpartAccentTint)
            }
            is LegalDocumentState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = state.message, color = ImpartWarning, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onRetry(state.document) },
                    colors = ButtonDefaults.buttonColors(containerColor = ImpartSurface, contentColor = ImpartTextPrimary)
                ) {
                    Text("TRY AGAIN")
                }
            }
            is LegalDocumentState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.blocks) { block -> LegalBlockView(block) }
            }
        }
    }
}

@Composable
private fun LegalBlockView(block: LegalBlock) {
    when (block) {
        is LegalBlock.Heading -> if (block.level == 1) {
            Text(text = block.text, color = ImpartTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        } else {
            Text(
                text = block.text.uppercase(),
                color = ImpartTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        is LegalBlock.Paragraph -> Text(text = block.text, color = ImpartTextPrimary, fontSize = 14.sp, lineHeight = 21.sp)
        is LegalBlock.Bullet -> Row {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, end = 10.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(ImpartTextMuted)
            )
            Text(text = block.text, color = ImpartTextPrimary, fontSize = 14.sp, lineHeight = 21.sp)
        }
        is LegalBlock.Preformatted -> Text(
            text = block.text,
            color = ImpartTextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFamily = ImpartMono
        )
    }
}
