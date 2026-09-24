// Engineered by uncoalesced
package com.uncoalesced.impart

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.domain.model.displayName
import com.uncoalesced.impart.presentation.contacts.ContactMatrixScreen
import com.uncoalesced.impart.presentation.dashboard.DashboardScreen
import com.uncoalesced.impart.presentation.decoy.NodeCorruptedScreen
import com.uncoalesced.impart.presentation.handshake.HandshakeViewModel
import com.uncoalesced.impart.presentation.handshake.QrGeneratorScreen
import com.uncoalesced.impart.presentation.handshake.QrScannerScreen
import com.uncoalesced.impart.presentation.handshake.ScanResult
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

    @Inject
    lateinit var duressManager: DuressManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMissingPermissions()

        setContent {
            ImpartTheme {
                // The decoy takes over the entire window, nav bar included, the instant the
                // duress PIN fires. Nothing behind it stays reachable.
                val corrupted by duressManager.isCorrupted.collectAsStateWithLifecycle()
                if (corrupted) NodeCorruptedScreen() else MainAppScreen()
            }
        }
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

private data class Tab(val index: Int, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(TAB_DASHBOARD, "Dashboard", Icons.Default.Home),
    Tab(TAB_MY_QR, "My QR", Icons.Default.QrCode),
    Tab(TAB_SCAN, "Scan QR", Icons.Default.QrCodeScanner),
    Tab(TAB_CONTACTS, "Contacts", Icons.Default.People)
)

@Composable
fun MainAppScreen() {
    var selectedTab by rememberSaveable { mutableStateOf(TAB_DASHBOARD) }
    var scanGeneration by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val handshakeVm: HandshakeViewModel = hiltViewModel()

    LaunchedEffect(handshakeVm) {
        handshakeVm.scanEvents.collect { event ->
            when (event) {
                is ScanResult.Added -> {
                    Toast.makeText(context, "Paired with ${event.contact.displayName}", Toast.LENGTH_SHORT).show()
                    selectedTab = TAB_CONTACTS
                }
                is ScanResult.Rejected -> {
                    Toast.makeText(context, event.reason, Toast.LENGTH_LONG).show()
                    scanGeneration += 1
                }
            }
        }
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
                TABS.forEach { tab ->
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
            }
        }
    }
}
