// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.dashboard

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uncoalesced.impart.core.util.ReadinessFix
import com.uncoalesced.impart.presentation.components.SecureAlertDialog
import com.uncoalesced.impart.core.util.ReadinessItem
import com.uncoalesced.impart.core.util.formatCountdown
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.displayName
import com.uncoalesced.impart.presentation.components.HoldToFireButton
import com.uncoalesced.impart.core.util.SensitiveClipboard
import com.uncoalesced.impart.presentation.security.ChangePinsFlow
import com.uncoalesced.impart.presentation.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HOUR_MS = 3_600_000L
private val DMS_INTERVAL_CHOICES = listOf(0, 4, 8, 12, 24)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenMyQr: () -> Unit,
    onOpenScanner: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val contacts by viewModel.activeContacts.collectAsStateWithLifecycle()
    val isCloaked by viewModel.isCloaked.collectAsStateWithLifecycle()
    val dmsIntervalHours by viewModel.dmsIntervalHours.collectAsStateWithLifecycle()
    val dmsLastCheckin by viewModel.dmsLastCheckin.collectAsStateWithLifecycle()
    val locationSharingEnabled by viewModel.locationSharingEnabled.collectAsStateWithLifecycle()
    val nodeIdentity by viewModel.nodeIdentity.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val shufflePad by viewModel.shufflePinPad.collectAsStateWithLifecycle()
    var showChangePins by remember { mutableStateOf(false) }
    var pendingVendorConfirm by remember { mutableStateOf<ReadinessFix.ConfirmVendorSettings?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val readinessPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { viewModel.refreshReadiness() }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            val granted = grants.values.any { it }
            viewModel.setLocationSharingEnabled(granted)
            if (!granted) {
                Toast.makeText(context, "Location permission denied. Location sharing stays off.", Toast.LENGTH_LONG).show()
            }
        }
    )

    LifecycleResumeEffect(Unit) {
        viewModel.refreshReadiness()
        onPauseOrDispose { }
    }

    LaunchedEffect(dmsIntervalHours) {
        while (dmsIntervalHours > 0) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is DashboardUiState.Success) {
            delay(3000)
            viewModel.resetState()
        }
    }

    pendingVendorConfirm?.let { fix ->
        SecureAlertDialog(
            onDismissRequest = { pendingVendorConfirm = null },
            containerColor = ImpartBackground,
            titleContentColor = ImpartTextPrimary,
            textContentColor = ImpartSecondary,
            title = { Text("DID YOU ALLOW IT?") },
            text = { Text("${fix.instruction} Impart cannot read these settings back, so the row stays amber until you confirm.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmVendorSettings(true)
                    pendingVendorConfirm = null
                }) {
                    Text("YES, ALLOWED", color = ImpartVerified, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.confirmVendorSettings(false)
                    pendingVendorConfirm = null
                }) {
                    Text("NOT YET", color = ImpartSecondary)
                }
            }
        )
    }

    if (showChangePins) {
        ChangePinsFlow(
            shufflePad = shufflePad,
            onVerify = viewModel::verifySecurityPin,
            onSave = viewModel::updatePins,
            onFinished = { showChangePins = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartGround)
            .verticalScroll(rememberScrollState())
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
            IconButton(onClick = { showChangePins = true }) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Change PINs",
                    tint = ImpartPanicRed
                )
            }
        }

        DashboardCard {
            CardHeader(icon = Icons.Default.Fingerprint, title = "MY NODE IDENTITY")
            Spacer(modifier = Modifier.height(8.dp))
            when (val identity = nodeIdentity) {
                is NodeIdentityState.Ready -> {
                    IdentityRow(label = "UUID", value = identity.uuid) {
                        SensitiveClipboard.copy(context, identity.uuid)
                        Toast.makeText(context, "UUID copied, cleared in 60 s", Toast.LENGTH_SHORT).show()
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    IdentityRow(label = "PHONE NUMBER", value = identity.phoneNumber) {
                        SensitiveClipboard.copy(context, identity.phoneNumber)
                        Toast.makeText(context, "Phone number copied, cleared in 60 s", Toast.LENGTH_SHORT).show()
                    }
                }
                is NodeIdentityState.Unavailable -> {
                    IdentityRow(label = "UUID", value = identity.uuid) {
                        SensitiveClipboard.copy(context, identity.uuid)
                        Toast.makeText(context, "UUID copied, cleared in 60 s", Toast.LENGTH_SHORT).show()
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "PHONE NUMBER UNAVAILABLE: ${identity.message}",
                        color = ImpartAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        DashboardCard {
            CardHeader(icon = Icons.Default.CheckCircle, title = "NODE READINESS")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Every row except Build must be green before the Doze test can pass. Tap a row to fix it.",
                color = ImpartSecondary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            readiness.forEach { item ->
                ReadinessRow(item = item) {
                    when (val fix = item.fix) {
                        is ReadinessFix.RequestPermissions ->
                            readinessPermissionLauncher.launch(fix.permissions.toTypedArray())
                        is ReadinessFix.OpenSettings -> try {
                            context.startActivity(fix.intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "This device has no settings page for that.", Toast.LENGTH_LONG).show()
                        }
                        ReadinessFix.EnterPhoneNumber -> onOpenMyQr()
                        ReadinessFix.PairContact -> onOpenScanner()
                        is ReadinessFix.ConfirmVendorSettings -> {
                            try {
                                context.startActivity(fix.intent)
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, "This device has no settings page for that.", Toast.LENGTH_LONG).show()
                            }
                            pendingVendorConfirm = fix
                        }
                        null -> Unit
                    }
                }
            }
        }

        DashboardCard {
            ToggleRow(
                icon = if (isCloaked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                title = if (isCloaked) "CLOAKED (CALCULATOR)" else "STANDARD LAUNCHER",
                subtitle = "Disguise app on home launcher",
                checked = isCloaked,
                onCheckedChange = { viewModel.toggleCloakMode(it) }
            )
        }

        DashboardCard {
            ToggleRow(
                icon = Icons.Default.LocationOn,
                title = "SHARE LOCATION ON PANIC",
                subtitle = "Attach coordinates to outgoing alerts",
                checked = locationSharingEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        viewModel.setLocationSharingEnabled(false)
                        return@ToggleRow
                    }
                    val granted = LOCATION_PERMISSIONS.any {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (granted) {
                        viewModel.setLocationSharingEnabled(true)
                    } else {
                        locationPermissionLauncher.launch(LOCATION_PERMISSIONS.toTypedArray())
                    }
                }
            )
        }

        DashboardCard(bottomPadding = 24.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CardHeader(icon = Icons.Default.Timer, title = "DEAD MAN'S SWITCH")
                Spacer(modifier = Modifier.height(16.dp))

                val formatter = remember { SimpleDateFormat("HH:mm - MMM dd", Locale.getDefault()) }
                val lastCheckinText = if (dmsLastCheckin > 0L) formatter.format(Date(dmsLastCheckin)) else "Never"

                Text(
                    text = "Interval: ${if (dmsIntervalHours == 0) "OFF" else "${dmsIntervalHours}H"}",
                    color = ImpartSecondary,
                    fontSize = 12.sp
                )
                Text(text = "Last Check-In: $lastCheckinText", color = ImpartSecondary, fontSize = 12.sp)

                if (dmsIntervalHours > 0 && dmsLastCheckin > 0L) {
                    val remainingMillis = dmsLastCheckin + dmsIntervalHours * HOUR_MS - nowMillis
                    val overdue = remainingMillis <= 0L
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (overdue) {
                            "CHECK-IN OVERDUE - DISPATCH PENDING"
                        } else {
                            "NEXT CHECK-IN REQUIRED IN: ${formatCountdown(remainingMillis)}"
                        },
                        color = if (overdue || remainingMillis < HOUR_MS) ImpartAmber else ImpartTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    DMS_INTERVAL_CHOICES.forEach { hours ->
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
            is DashboardUiState.Loading -> CircularProgressIndicator(color = ImpartPanicRed)
            is DashboardUiState.Success -> Text(
                text = state.message,
                color = ImpartVerified,
                style = MaterialTheme.typography.titleMedium
            )
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
                    Text("No trusted contacts found. Pair a node by QR first.", color = ImpartSecondary)
                } else {
                    HoldToFireButton(
                        label = "BROADCAST PANIC (ALL NODES)",
                        containerColor = ImpartPanicDark,
                        onFire = { viewModel.broadcastPanic(contacts) },
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Hold a contact button to INITIATE PANIC:", color = ImpartSecondary)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Plain Column so the whole dashboard is one scroll container. Contacts are
                    // bounded by physical QR pairings, so a lazy list buys nothing here.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        contacts.forEach { contact ->
                            PanicButton(contact = contact) { viewModel.initiatePanic(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PanicButton(contact: Contact, onPanic: (Contact) -> Unit) {
    Column {
        HoldToFireButton(
            label = "INITIATE PANIC: ${contact.displayName}",
            containerColor = ImpartPanicRed,
            onFire = { onPanic(contact) },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            fontSize = 20.sp
        )
        if (contact.needsRepair) {
            Text(
                text = "Cannot page ${contact.displayName} until you re-pair: your keys were rotated.",
                color = ImpartAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DashboardCard(
    bottomPadding: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ImpartBackground),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), content = content)
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = ImpartSecondary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, color = ImpartTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(imageVector = icon, contentDescription = null, tint = ImpartSecondary)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, color = ImpartTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = ImpartSecondary, fontSize = 10.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ImpartTextPrimary,
                checkedTrackColor = ImpartPanicRed,
                uncheckedThumbColor = ImpartSecondary,
                uncheckedTrackColor = ImpartGround
            )
        )
    }
}

@Composable
private fun ReadinessRow(item: ReadinessItem, onFix: () -> Unit) {
    val tint = if (item.ok) ImpartVerified else ImpartAmber
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.fix != null) Modifier.clickable(onClick = onFix) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.label, color = ImpartTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = item.detail, color = tint, fontSize = 10.sp)
        }
        if (item.fix != null) {
            Text(text = "FIX", color = ImpartAmber, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun IdentityRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = ImpartSecondary, fontSize = 9.sp)
            Text(
                text = value,
                color = ImpartTextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = ImpartSecondary
            )
        }
    }
}

private val LOCATION_PERMISSIONS = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)
