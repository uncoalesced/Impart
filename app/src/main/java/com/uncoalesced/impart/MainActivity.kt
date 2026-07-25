// Engineered by uncoalesced
package com.uncoalesced.impart

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.presentation.contacts.ContactMatrixScreen
import com.uncoalesced.impart.presentation.contacts.ContactMatrixViewModel
import com.uncoalesced.impart.presentation.dashboard.DashboardScreen
import com.uncoalesced.impart.presentation.dashboard.DashboardViewModel
import com.uncoalesced.impart.presentation.decoy.NodeCorruptedScreen
import com.uncoalesced.impart.presentation.handshake.HandshakeViewModel
import com.uncoalesced.impart.presentation.handshake.QrGeneratorScreen
import com.uncoalesced.impart.presentation.handshake.QrScannerScreen
import com.uncoalesced.impart.presentation.theme.ImpartBackground
import com.uncoalesced.impart.presentation.theme.ImpartPanicRed
import com.uncoalesced.impart.presentation.theme.ImpartSecondary
import com.uncoalesced.impart.presentation.theme.ImpartTextPrimary
import com.uncoalesced.impart.presentation.theme.ImpartTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var duressManager: DuressManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            ImpartTheme {
                if (duressManager.isNodeCorrupted()) {
                    NodeCorruptedScreen()
                } else {
                    MainAppScreen()
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    val localUuid = remember { UUID.randomUUID().toString() }
    val localFcmToken = "fcm_token_placeholder"

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = ImpartBackground,
                contentColor = ImpartTextPrimary
            ) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ImpartPanicRed,
                    selectedTextColor = ImpartPanicRed,
                    unselectedIconColor = ImpartSecondary,
                    unselectedTextColor = ImpartSecondary,
                    indicatorColor = ImpartBackground
                )
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = "My QR") },
                    label = { Text("My QR") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan") },
                    label = { Text("Scan QR") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.People, contentDescription = "Contacts") },
                    label = { Text("Contacts") },
                    colors = navColors
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> {
                    val dashboardVm: DashboardViewModel = hiltViewModel()
                    DashboardScreen(viewModel = dashboardVm)
                }
                1 -> {
                    val handshakeVm: HandshakeViewModel = hiltViewModel()
                    LaunchedEffect(Unit) {
                        handshakeVm.generatePayload(localUuid, localFcmToken)
                    }
                    val payloadJson by handshakeVm.localPayloadJson.collectAsState()
                    QrGeneratorScreen(payloadJson = payloadJson ?: "")
                }
                2 -> {
                    val handshakeVm: HandshakeViewModel = hiltViewModel()
                    QrScannerScreen(onQrScanned = { json ->
                        handshakeVm.onQrScanned(json)
                        selectedTab = 3
                    })
                }
                3 -> {
                    val matrixVm: ContactMatrixViewModel = hiltViewModel()
                    ContactMatrixScreen(viewModel = matrixVm)
                }
            }
        }
    }
}
