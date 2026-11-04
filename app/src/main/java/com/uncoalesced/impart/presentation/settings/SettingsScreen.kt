// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uncoalesced.impart.BuildConfig
import com.uncoalesced.impart.core.security.LockoutPolicy
import com.uncoalesced.impart.core.security.UnlockResult
import com.uncoalesced.impart.domain.model.LegalDocument
import com.uncoalesced.impart.presentation.legal.LegalDocumentScreen
import com.uncoalesced.impart.presentation.legal.LegalDocumentState
import com.uncoalesced.impart.presentation.legal.LegalViewModel
import com.uncoalesced.impart.presentation.security.ChangePinsFlow
import com.uncoalesced.impart.presentation.security.SecurityAuthDialog
import com.uncoalesced.impart.presentation.theme.*

@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel, legalViewModel: LegalViewModel) {
    val documentState by legalViewModel.state.collectAsStateWithLifecycle()
    val fingerprint by settingsViewModel.ownFingerprint.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showChangePins by remember { mutableStateOf(false) }
    val wipeAfterFailures by settingsViewModel.wipeAfterFailedAttempts.collectAsStateWithLifecycle()
    val shufflePad by settingsViewModel.shufflePinPad.collectAsStateWithLifecycle()
    var pendingWipeToggle by remember { mutableStateOf<Boolean?>(null) }

    if (documentState != LegalDocumentState.Closed) {
        LegalDocumentScreen(state = documentState, onBack = legalViewModel::close, onRetry = legalViewModel::open)
        return
    }

    if (showChangePins) {
        ChangePinsFlow(
            shufflePad = shufflePad,
            onVerify = settingsViewModel::verifyPin,
            onSave = settingsViewModel::updatePins,
            onFinished = { showChangePins = false }
        )
    }

    pendingWipeToggle?.let { wanted ->
        SecurityAuthDialog(
            shufflePad = shufflePad,
            onDismiss = { pendingWipeToggle = null },
            onPinSubmit = { pin ->
                when (settingsViewModel.verifyPin(pin)) {
                    UnlockResult.Unlocked -> settingsViewModel.setWipeAfterFailedAttempts(wanted)
                    is UnlockResult.Invalid -> Toast.makeText(context, "Invalid PIN", Toast.LENGTH_SHORT).show()
                    is UnlockResult.LockedOut -> Toast.makeText(context, "Too many wrong attempts. Try again later.", Toast.LENGTH_SHORT).show()
                    UnlockResult.Wiped -> Unit
                }
                pendingWipeToggle = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartGround)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "SETTINGS",
            color = ImpartTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        Section("LEGAL") {
            LegalDocument.entries.forEachIndexed { index, document ->
                if (index > 0) Divider()
                NavRow(title = document.title) { legalViewModel.open(document) }
            }
        }

        Section("SECURITY") {
            NavRow(title = "Change PINs") { showChangePins = true }
            Divider()
            SwitchRow(
                title = "Shuffle the PIN pad",
                subtitle = "A different key layout every time the pad appears, so smudges and shoulder surfers learn less.",
                checked = shufflePad,
                onCheckedChange = settingsViewModel::setShufflePinPad
            )
            Divider()
            SwitchRow(
                title = "Wipe after ${LockoutPolicy.HARD_CAP_ATTEMPTS} wrong PINs",
                subtitle = "Off by default. When on, ${LockoutPolicy.HARD_CAP_ATTEMPTS} wrong PINs in a row wipe this node exactly like the duress PIN. Lockouts still apply between attempts.",
                checked = wipeAfterFailures,
                onCheckedChange = { pendingWipeToggle = it }
            )
            Divider()
            InfoRow(
                title = "My key fingerprint",
                value = fingerprint ?: "Computing...",
                monospace = true,
                hint = "Read this aloud when pairing. The other phone shows it next to your name."
            )
        }

        Section("ABOUT") {
            InfoRow(title = "Version", value = BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) " (debug build)" else "")
            Divider()
            InfoRow(title = "License", value = "Apache License 2.0")
            Divider()
            NavRow(title = "Source code", subtitle = LegalDocument.SOURCE_URL) {
                open(context, Intent(Intent.ACTION_VIEW, Uri.parse(LegalDocument.SOURCE_URL)))
            }
            Divider()
            NavRow(title = "Contact", subtitle = LegalDocument.CONTACT_EMAIL) {
                open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${LegalDocument.CONTACT_EMAIL}")))
            }
            Divider()
            NavRow(title = "Report an issue", subtitle = LegalDocument.ISSUES_URL) {
                open(context, Intent(Intent.ACTION_VIEW, Uri.parse(LegalDocument.ISSUES_URL)))
            }
        }
    }
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open that.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        color = ImpartSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = ImpartBackground),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = ImpartGround, thickness = 1.dp)

@Composable
private fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = ImpartTextPrimary, fontSize = 15.sp)
            if (subtitle != null) Text(text = subtitle, color = ImpartSecondary, fontSize = 11.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ImpartSecondary)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = ImpartTextPrimary, fontSize = 15.sp)
            Text(text = subtitle, color = ImpartSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = ImpartTextPrimary, checkedTrackColor = ImpartPanicRed)
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String, monospace: Boolean = false, hint: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(text = title, color = ImpartSecondary, fontSize = 11.sp)
        Text(
            text = value,
            color = ImpartTextPrimary,
            fontSize = 15.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        )
        if (hint != null) Text(text = hint, color = ImpartSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
