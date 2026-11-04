// Engineered by uncoalesced
package com.uncoalesced.impart

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uncoalesced.impart.core.security.AppLockManager
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.sms.DefaultSmsRole
import com.uncoalesced.impart.presentation.messages.MessagesScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.uncoalesced.impart.core.util.WindowHardening
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.LegalDocument
import com.uncoalesced.impart.domain.model.displayName
import com.uncoalesced.impart.presentation.components.SecureAlertDialog
import com.uncoalesced.impart.presentation.components.rejectObscuredTouches
import com.uncoalesced.impart.presentation.components.rememberObscuredTouchNotifier
import com.uncoalesced.impart.presentation.contacts.ContactMatrixScreen
import com.uncoalesced.impart.presentation.dashboard.DashboardScreen
import com.uncoalesced.impart.presentation.decoy.NodeCorruptedScreen
import com.uncoalesced.impart.presentation.handshake.HandshakeViewModel
import com.uncoalesced.impart.presentation.handshake.QrGeneratorScreen
import com.uncoalesced.impart.presentation.handshake.QrScannerScreen
import com.uncoalesced.impart.presentation.handshake.ScanResult
import com.uncoalesced.impart.presentation.legal.LegalAcceptanceScreen
import com.uncoalesced.impart.presentation.security.AppLockScreen
import com.uncoalesced.impart.presentation.security.PinSetupDialog
import com.uncoalesced.impart.presentation.settings.SettingsScreen
import com.uncoalesced.impart.presentation.theme.ImpartAmber
import com.uncoalesced.impart.presentation.theme.ImpartBackground
import com.uncoalesced.impart.presentation.theme.ImpartGround
import com.uncoalesced.impart.presentation.theme.ImpartPanicRed
import com.uncoalesced.impart.presentation.theme.ImpartSecondary
import com.uncoalesced.impart.presentation.theme.ImpartTextPrimary
import com.uncoalesced.impart.presentation.theme.ImpartTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        // From ComposeSmsActivity (default SMS mode): open the Texts tab on this number.
        const val EXTRA_COMPOSE_TO = "composeTo"
        const val EXTRA_COMPOSE_BODY = "composeBody"
    }

    @Inject
    lateinit var duressManager: DuressManager

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var securePrefsManager: SecurePrefsManager

    @Inject
    lateinit var defaultSmsRole: DefaultSmsRole

    // Who holds the SMS role can change in system settings while the app is away, so it is
    // re-read on every resume. The Texts tab exists only while Impart holds it.
    private val isDefaultSmsApp = MutableStateFlow(false)
    private val composeRequest = MutableStateFlow<Pair<String?, String?>?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps the QR, contacts and fingerprints out of screenshots, screen recorders and
        // the recent-apps preview.
        WindowHardening.harden(this)

        setContent {
            ImpartTheme {
                val corrupted by duressManager.isCorrupted.collectAsStateWithLifecycle()
                val acceptedLegal by securePrefsManager.acceptedLegalVersion.collectAsStateWithLifecycle()
                val pinsChosen by securePrefsManager.hasCustomPins.collectAsStateWithLifecycle()
                val unlocked by appLockManager.isUnlocked.collectAsStateWithLifecycle()
                val shufflePad by securePrefsManager.shufflePinPad.collectAsStateWithLifecycle()

                // Order matters. The decoy wins over everything once the duress PIN has fired.
                // Then the terms (first run, or after they change), then choosing PINs, then
                // the lock. Nothing behind the lock is composed until the right PIN is entered.
                when {
                    corrupted -> NodeCorruptedScreen()
                    acceptedLegal < LegalDocument.CURRENT_VERSION -> LegalAcceptanceScreen(
                        viewModel = hiltViewModel(),
                        onDecline = { finish() }
                    )
                    !pinsChosen -> Box(modifier = Modifier.fillMaxSize().background(ImpartGround)) {
                        PinSetupDialog(
                            shufflePad = shufflePad,
                            required = true,
                            onDismiss = {},
                            onSave = { standard, duress ->
                                duressManager.updatePins(standard, duress).onSuccess { appLockManager.unlockAfterSetup() }
                            }
                        )
                    }
                    !unlocked -> AppLockScreen(shufflePad = shufflePad, onSubmit = appLockManager::submitPin)
                    else -> {
                        LaunchedEffect(Unit) { requestMissingPermissions() }
                        val smsDefault by isDefaultSmsApp.collectAsStateWithLifecycle()
                        val compose by composeRequest.collectAsStateWithLifecycle()
                        MainAppScreen(
                            isDefaultSmsApp = smsDefault,
                            composeRequest = compose,
                            onComposeHandled = { composeRequest.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLockManager.onForegrounded()
    }

    override fun onResume() {
        super.onResume()
        isDefaultSmsApp.value = defaultSmsRole.isDefault()
        readComposeRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readComposeRequest(intent)
    }

    private fun readComposeRequest(intent: Intent?) {
        val to = intent?.getStringExtra(EXTRA_COMPOSE_TO) ?: return
        composeRequest.value = to to intent.getStringExtra(EXTRA_COMPOSE_BODY)
        intent.removeExtra(EXTRA_COMPOSE_TO)
        intent.removeExtra(EXTRA_COMPOSE_BODY)
    }

    // Screen off locks at once. Anything else (a Settings page, a permission prompt) gets the
    // 30 s grace period before the lock comes back.
    override fun onStop() {
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) {
            appLockManager.lock()
        } else {
            appLockManager.onBackgrounded()
        }
        super.onStop()
    }

    // Location is deliberately absent: it is requested only from the dashboard toggle.
    // Everything else the readiness card can re-request later with a proper explanation.
    private fun requestMissingPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}

private const val TAB_DASHBOARD = 0
private const val TAB_MY_QR = 1
private const val TAB_SCAN = 2
private const val TAB_CONTACTS = 3
private const val TAB_SETTINGS = 4
private const val TAB_TEXTS = 5

private data class Tab(val index: Int, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(TAB_DASHBOARD, "Dashboard", Icons.Default.Home),
    Tab(TAB_MY_QR, "My QR", Icons.Default.QrCode),
    Tab(TAB_SCAN, "Scan QR", Icons.Default.QrCodeScanner),
    Tab(TAB_CONTACTS, "Contacts", Icons.Default.People),
    Tab(TAB_SETTINGS, "Settings", Icons.Default.Settings)
)
private val TEXTS_TAB = Tab(TAB_TEXTS, "Texts", Icons.Default.Message)

@Composable
fun MainAppScreen(
    isDefaultSmsApp: Boolean = false,
    composeRequest: Pair<String?, String?>? = null,
    onComposeHandled: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_DASHBOARD) }
    val tabs = if (isDefaultSmsApp) TABS + TEXTS_TAB else TABS
    if (!isDefaultSmsApp && selectedTab == TAB_TEXTS) selectedTab = TAB_DASHBOARD
    LaunchedEffect(composeRequest) {
        if (composeRequest != null && isDefaultSmsApp) selectedTab = TAB_TEXTS
    }
    var scanGeneration by remember { mutableIntStateOf(0) }
    var pendingKeyChange by remember { mutableStateOf<ScanResult.ConfirmKeyChange?>(null) }
    val context = LocalContext.current
    val handshakeVm: HandshakeViewModel = hiltViewModel()

    LaunchedEffect(handshakeVm) {
        handshakeVm.scanEvents.collect { event ->
            when (event) {
                is ScanResult.Added -> {
                    Toast.makeText(context, "Paired with ${event.contact.displayName}", Toast.LENGTH_SHORT).show()
                    selectedTab = TAB_CONTACTS
                }
                is ScanResult.Refreshed -> {
                    Toast.makeText(context, "Re-paired with ${event.contact.displayName}", Toast.LENGTH_SHORT).show()
                    selectedTab = TAB_CONTACTS
                }
                is ScanResult.ConfirmKeyChange -> pendingKeyChange = event
                is ScanResult.Rejected -> {
                    Toast.makeText(context, event.reason, Toast.LENGTH_LONG).show()
                    scanGeneration += 1
                }
            }
        }
    }

    pendingKeyChange?.let { change ->
        KeyChangeDialog(
            change = change,
            onAccept = {
                handshakeVm.confirmKeyChange(change.existing, change.candidate)
                pendingKeyChange = null
            },
            onReject = {
                pendingKeyChange = null
                scanGeneration += 1
                Toast.makeText(context, "Kept the existing keys for ${change.existing.displayName}.", Toast.LENGTH_LONG).show()
            }
        )
    }

    Scaffold(
        containerColor = ImpartGround,
        bottomBar = {
            NavigationBar(containerColor = ImpartBackground, contentColor = ImpartTextPrimary) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ImpartPanicRed,
                    selectedTextColor = ImpartPanicRed,
                    unselectedIconColor = ImpartSecondary,
                    unselectedTextColor = ImpartSecondary,
                    indicatorColor = ImpartBackground
                )
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab.index,
                        onClick = { selectedTab = tab.index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = navColors
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                TAB_DASHBOARD -> DashboardScreen(
                    viewModel = hiltViewModel(),
                    onOpenMyQr = { selectedTab = TAB_MY_QR },
                    onOpenScanner = { selectedTab = TAB_SCAN }
                )
                TAB_MY_QR -> {
                    val qrState by handshakeVm.qrState.collectAsStateWithLifecycle()
                    val phoneNumber by handshakeVm.localPhoneNumber.collectAsStateWithLifecycle()
                    QrGeneratorScreen(
                        qrState = qrState,
                        phoneNumber = phoneNumber,
                        onPhoneNumberChange = handshakeVm::setLocalPhoneNumber
                    )
                }
                TAB_SCAN -> QrScannerScreen(
                    scanGeneration = scanGeneration,
                    onQrScanned = handshakeVm::onQrScanned
                )
                TAB_CONTACTS -> ContactMatrixScreen(
                    viewModel = hiltViewModel(),
                    onMyQr = { selectedTab = TAB_MY_QR },
                    onScanQr = { selectedTab = TAB_SCAN }
                )
                TAB_SETTINGS -> SettingsScreen(
                    settingsViewModel = hiltViewModel(),
                    legalViewModel = hiltViewModel()
                )
                TAB_TEXTS -> {
                    MessagesScreen(
                        viewModel = hiltViewModel(),
                        composeTo = composeRequest?.first,
                        composeBody = composeRequest?.second
                    )
                    LaunchedEffect(composeRequest) { if (composeRequest != null) onComposeHandled() }
                }
            }
        }
    }
}

// Shown when a scanned QR reuses a known contact's UUID with different keys. That is what a
// reinstall looks like, and also what a QR built to hijack the contact looks like, so the
// user decides with both fingerprints in front of them.
@Composable
private fun KeyChangeDialog(
    change: ScanResult.ConfirmKeyChange,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val rejectNotifier = rememberObscuredTouchNotifier()
    SecureAlertDialog(
        onDismissRequest = onReject,
        containerColor = ImpartBackground,
        titleContentColor = ImpartTextPrimary,
        textContentColor = ImpartSecondary,
        title = { Text("KEYS CHANGED FOR ${change.existing.displayName}") },
        text = {
            Column {
                Text("This QR claims to be ${change.existing.displayName} but carries different keys. Only accept if you are standing with them and they reinstalled Impart.")
                Spacer(modifier = Modifier.height(12.dp))
                Text("OLD KEY", color = ImpartSecondary, fontWeight = FontWeight.Bold)
                Text(change.oldFingerprint, color = ImpartTextPrimary, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(8.dp))
                Text("NEW KEY", color = ImpartAmber, fontWeight = FontWeight.Bold)
                Text(change.newFingerprint, color = ImpartTextPrimary, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept, modifier = Modifier.rejectObscuredTouches(rejectNotifier)) {
                Text("ACCEPT NEW KEYS", color = ImpartAmber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("KEEP OLD KEYS", color = ImpartTextPrimary)
            }
        }
    )
}
