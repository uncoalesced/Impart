// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.contacts

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.displayName
import com.uncoalesced.impart.presentation.theme.*
import java.util.concurrent.TimeUnit

private const val REVERIFY_THRESHOLD_DAYS = 30L

@Composable
fun ContactMatrixScreen(
    viewModel: ContactMatrixViewModel,
    onMyQr: () -> Unit,
    onScanQr: () -> Unit
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingRevoke by remember { mutableStateOf<Contact?>(null) }
    var pendingRename by remember { mutableStateOf<Contact?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    pendingRevoke?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            containerColor = ImpartBackground,
            titleContentColor = ImpartTextPrimary,
            textContentColor = ImpartSecondary,
            title = { Text("REVOKE ${target.displayName}") },
            text = {
                Text("This contact will no longer receive your panic signals, and theirs will no longer raise your alarm. Re-pairing requires a new physical QR handshake.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeContact(target.uuid)
                    pendingRevoke = null
                }) {
                    Text("REVOKE", color = ImpartPanicRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }) {
                    Text("CANCEL", color = ImpartSecondary)
                }
            }
        )
    }

    pendingRename?.let { target ->
        RenameContactDialog(
            contact = target,
            onDismiss = { pendingRename = null },
            onSave = { nickname ->
                viewModel.renameContact(target.uuid, nickname)
                pendingRename = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartGround)
            .padding(24.dp)
    ) {
        Text(
            text = "CONTACT MATRIX",
            color = ImpartTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onScanQr,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ImpartTextPrimary)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SCAN QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onMyQr,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ImpartTextPrimary)
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("MY QR CODE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (contacts.isEmpty()) {
            Text(
                text = "No trusted nodes yet. Pairing is a physical act: scan their QR, then let them scan yours.",
                color = ImpartSecondary,
                fontSize = 12.sp
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(contacts, key = { it.uuid }) { contact ->
                ContactRow(
                    contact = contact,
                    onRename = { pendingRename = it },
                    onRevoke = { pendingRevoke = it }
                )
            }
        }
    }
}

@Composable
fun ContactRow(contact: Contact, onRename: (Contact) -> Unit, onRevoke: (Contact) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ImpartBackground),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onRename(contact) }
            ) {
                Text(
                    text = contact.displayName,
                    color = ImpartTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = contact.phoneNumber ?: "NO NUMBER",
                    color = ImpartSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = contact.uuid,
                    color = ImpartSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                VerificationBadge(addedAt = contact.addedAt)
                Text(text = "Tap to rename", color = ImpartSecondary.copy(alpha = 0.7f), fontSize = 9.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { onRevoke(contact) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpartPanicRed,
                    contentColor = ImpartTextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "REVOKE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RenameContactDialog(contact: Contact, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var nickname by remember(contact.uuid) { mutableStateOf(contact.nickname.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ImpartBackground,
        titleContentColor = ImpartTextPrimary,
        textContentColor = ImpartSecondary,
        title = { Text("RENAME NODE") },
        text = {
            Column {
                Text("A name you will recognise on the panic button and the intrusion screen.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("NICKNAME") },
                    singleLine = true,
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nickname) }, enabled = nickname.isNotBlank()) {
                Text("SAVE", color = ImpartPanicRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = ImpartSecondary)
            }
        }
    )
}

// Age of the QR handshake, not live presence. Contact carries no last-seen field and SMS has
// no heartbeat, so this can only say "verified N days ago", never "reachable now".
@Composable
private fun VerificationBadge(addedAt: Long) {
    val ageDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - addedAt)
    val stale = ageDays >= REVERIFY_THRESHOLD_DAYS
    val tint = if (stale) ImpartAmber else ImpartVerified
    val label = when {
        stale -> "LAST VERIFIED ${ageDays}D AGO - CONSIDER RE-PAIRING"
        ageDays <= 0L -> "LAST VERIFIED TODAY"
        else -> "LAST VERIFIED ${ageDays}D AGO"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = label }
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
