// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.messages

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uncoalesced.impart.core.util.TimeFormat
import com.uncoalesced.impart.domain.model.SmsConversation
import com.uncoalesced.impart.domain.model.SmsMessageItem
import com.uncoalesced.impart.presentation.theme.ImpartAmber
import com.uncoalesced.impart.presentation.theme.ImpartBackground
import com.uncoalesced.impart.presentation.theme.ImpartGround
import com.uncoalesced.impart.presentation.theme.ImpartPanicRed
import com.uncoalesced.impart.presentation.theme.ImpartSecondary
import com.uncoalesced.impart.presentation.theme.ImpartTextPrimary

// Minimal SMS client, shown only while Impart is the default SMS app and only behind the
// lock. Conversation list, one thread, a compose box. MMS appears as a notice.
@Composable
fun MessagesScreen(viewModel: MessagesViewModel, composeTo: String?, composeBody: String?) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val openAddress by viewModel.openAddress.collectAsStateWithLifecycle()
    val thread by viewModel.thread.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var newAddress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(composeTo) {
        if (!composeTo.isNullOrBlank()) viewModel.open(composeTo)
    }

    val address = openAddress ?: newAddress
    if (address != null || newAddress != null) {
        ThreadView(
            address = address.orEmpty(),
            editableAddress = openAddress == null,
            messages = thread,
            initialDraft = composeBody.orEmpty(),
            onAddressChange = { newAddress = it },
            onBack = {
                viewModel.open(null)
                newAddress = null
            },
            onSend = { to, text -> viewModel.send(to, text) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(ImpartGround).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "TEXTS", color = ImpartTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { newAddress = "" }) { Text("NEW", color = ImpartPanicRed, fontWeight = FontWeight.Bold) }
        }
        Text(
            text = "Ordinary texts, stored on this phone only. Impart alerts never appear here.",
            color = ImpartSecondary,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (conversations.isEmpty()) {
            Text("No texts yet.", color = ImpartSecondary)
        }
        LazyColumn {
            items(conversations, key = { it.address }) { conversation ->
                ConversationRow(conversation) { viewModel.open(conversation.address) }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: SmsConversation, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .background(ImpartBackground, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = conversation.address,
                color = if (conversation.unread) ImpartAmber else ImpartTextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(text = TimeFormat.clock(conversation.lastAt), color = ImpartSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Text(text = conversation.lastBody, color = ImpartSecondary, fontSize = 12.sp, maxLines = 2)
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ThreadView(
    address: String,
    editableAddress: Boolean,
    messages: List<SmsMessageItem>,
    initialDraft: String,
    onAddressChange: (String) -> Unit,
    onBack: () -> Unit,
    onSend: (String, String) -> Unit
) {
    var draft by remember { mutableStateOf(initialDraft) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().background(ImpartGround)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to texts", tint = ImpartTextPrimary)
            }
            if (editableAddress) {
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = { Text("TO (NUMBER)", color = ImpartSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrect = false),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(text = address, color = ImpartTextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            items(messages, key = { it.id }) { message -> Bubble(message) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message", color = ImpartSecondary) },
                // Password keyboard class with visible text: no learning, no suggestions.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
                visualTransformation = VisualTransformation.None,
                colors = fieldColors(),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    onSend(address, draft)
                    draft = ""
                },
                enabled = draft.isNotBlank() && address.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = ImpartPanicRed)
            }
        }
    }
}

@Composable
private fun Bubble(message: SmsMessageItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(if (message.outgoing) ImpartPanicRed.copy(alpha = 0.25f) else ImpartBackground, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column {
                Text(text = message.body, color = ImpartTextPrimary, fontSize = 14.sp)
                Text(text = TimeFormat.clock(message.at), color = ImpartSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ImpartTextPrimary,
    unfocusedTextColor = ImpartTextPrimary,
    focusedBorderColor = ImpartPanicRed,
    unfocusedBorderColor = ImpartSecondary,
    cursorColor = ImpartPanicRed
)
