// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.uncoalesced.impart.core.security.PinVerificationResult
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.presentation.decoy.NodeCorruptedScreen
import com.uncoalesced.impart.presentation.security.SecurityAuthDialog
import com.uncoalesced.impart.presentation.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val contacts by viewModel.activeContacts.collectAsState()
    val isCloaked by viewModel.isCloaked.collectAsState()
    
    val dmsIntervalHours by viewModel.dmsIntervalHours.collectAsState()
    val dmsLastCheckin by viewModel.dmsLastCheckin.collectAsState()

    val context = LocalContext.current
    var showAuthDialog by remember { mutableStateOf(false) }
    var smsFallbackEnabled by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        
        val missing = permissions.filter { 
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED 
        }
        
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is DashboardUiState.Success) {
            delay(3000)
            viewModel.resetState()
        }
    }

    if (viewModel.isNodeCorrupted() || uiState is DashboardUiState.NodeCorrupted) {
        NodeCorruptedScreen()
        return
    }

    if (showAuthDialog) {
        SecurityAuthDialog(
            onDismiss = { showAuthDialog = false },
            onPinSubmit = { pin ->
                showAuthDialog = false
                val res = viewModel.verifySecurityPin(pin)
                when (res) {
                    PinVerificationResult.STANDARD_SUCCESS -> {
                        Toast.makeText(context, "Standard PIN Authenticated", Toast.LENGTH_SHORT).show()
                    }
                    PinVerificationResult.DURESS_TRIGGERED -> {
                        Toast.makeText(context, "DURESS PIN TRIGGERED: Wiping Local Node Data!", Toast.LENGTH_LONG).show()
                    }
                    PinVerificationResult.INVALID_PIN -> {
                        Toast.makeText(context, "Invalid PIN Entered", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "IMPART DASHBOARD",
                color = ImpartTextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            IconButton(onClick = { showAuthDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Auth Security",
                    tint = ImpartPanicRed
                )
            }
        }

        // Stealth Cloak Switcher
        Card(
            colors = CardDefaults.cardColors(containerColor = ImpartSecondary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCloaked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Cloak Mode",
                        tint = ImpartSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isCloaked) "CLOAKED (CALCULATOR)" else "STANDARD LAUNCHER",
                            color = ImpartTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Disguise app on home launcher",
                            color = ImpartSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Switch(
                    checked = isCloaked,
                    onCheckedChange = { viewModel.toggleCloakMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ImpartTextPrimary,
                        checkedTrackColor = ImpartPanicRed,
                        uncheckedThumbColor = ImpartSecondary,
                        uncheckedTrackColor = ImpartBackground
                    )
                )
            }
        }

        // SMS Fallback Switcher
        Card(
            colors = CardDefaults.cardColors(containerColor = ImpartSecondary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = "SMS Fallback",
                        tint = ImpartSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SMS FALLBACK (OFF-GRID)",
                            color = ImpartTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Auto-fallback to encrypted SMS",
                            color = ImpartSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Switch(
                    checked = smsFallbackEnabled,
                    onCheckedChange = { smsFallbackEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ImpartTextPrimary,
                        checkedTrackColor = ImpartPanicRed,
                        uncheckedThumbColor = ImpartSecondary,
                        uncheckedTrackColor = ImpartBackground
                    )
                )
            }
        }

        // Dead Man's Switch Card
        Card(
            colors = CardDefaults.cardColors(containerColor = ImpartSecondary.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = "DMS", tint = ImpartSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEAD MAN'S SWITCH",
                        color = ImpartTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                val formatter = SimpleDateFormat("HH:mm - MMM dd", Locale.getDefault())
                val lastCheckinText = if (dmsLastCheckin > 0L) formatter.format(Date(dmsLastCheckin)) else "Never"
                
                Text(
                    text = "Interval: ${if (dmsIntervalHours == 0) "OFF" else "${dmsIntervalHours}H"}",
                    color = ImpartSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "Last Check-In: $lastCheckinText",
                    color = ImpartSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    listOf(0, 4, 8, 12, 24).forEach { hours ->
                        FilterChip(
                            selected = dmsIntervalHours == hours,
                            onClick = { viewModel.setDmsInterval(hours) },
                            label = { Text(if (hours == 0) "OFF" else "${hours}H") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ImpartPanicRed,
                                selectedLabelColor = ImpartTextPrimary,
                                labelColor = ImpartSecondary
                            )
                        )
                    }
                }
                
                if (dmsIntervalHours > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.dmsCheckIn() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImpartSecondary.copy(alpha = 0.5f),
                            contentColor = ImpartTextPrimary
                        )
                    ) {
                        Text("CHECK IN (RESET TIMER)")
                    }
                }
            }
        }

        when (val state = uiState) {
            is DashboardUiState.Loading -> {
                CircularProgressIndicator(color = ImpartPanicRed)
            }
            is DashboardUiState.Success -> {
                Text(
                    text = "Panic Dispatched Successfully", 
                    color = ImpartTextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            is DashboardUiState.Error -> {
                Text("Error: ${state.message}", color = ImpartPanicRed)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.resetState() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImpartPanicRed,
                        contentColor = ImpartTextPrimary
                    )
                ) {
                    Text("Retry", color = ImpartTextPrimary)
                }
            }
            is DashboardUiState.Idle -> {
                if (contacts.isEmpty()) {
                    Text("No trusted contacts found. Add a contact first.", color = ImpartSecondary)
                } else {
                    Button(
                        onClick = { viewModel.broadcastPanic(contacts) },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImpartPanicDark,
                            contentColor = ImpartTextPrimary
                        )
                    ) {
                        Text(
                            text = "BROADCAST PANIC (ALL NODES)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Select a contact to INITIATE PANIC:", color = ImpartSecondary)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(contacts) { contact ->
                            PanicButton(contact = contact) {
                                viewModel.initiatePanic(it)
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
fun PanicButton(contact: Contact, onPanic: (Contact) -> Unit) {
    Button(
        onClick = { onPanic(contact) },
        modifier = Modifier.fillMaxWidth().height(80.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ImpartPanicRed,
            contentColor = ImpartTextPrimary
        )
    ) {
        Text(
            text = "INITIATE PANIC: ${contact.nickname ?: contact.uuid.take(8)}",
            style = MaterialTheme.typography.titleLarge
        )
    }
}
