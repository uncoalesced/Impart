// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.uncoalesced.impart.BuildConfig
import com.uncoalesced.impart.core.security.SigningIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ReadinessFix {
    data class RequestPermissions(val permissions: List<String>) : ReadinessFix
    data class OpenSettings(val intent: Intent) : ReadinessFix
    data object EnterPhoneNumber : ReadinessFix
    data object PairContact : ReadinessFix
    // Opens a vendor settings screen that cannot be read back; the user confirms afterwards.
    data class ConfirmVendorSettings(val intent: Intent, val instruction: String) : ReadinessFix
}

data class ReadinessItem(
    val label: String,
    val ok: Boolean,
    val detail: String,
    val fix: ReadinessFix?
)

// Everything that has to be true on a real phone before the Doze test can pass. Each
// failing row knows how to open the exact Settings page that fixes it.
@Singleton
class NodeReadinessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val oemSettings: OemSettingsResolver
) {
    fun evaluate(phoneNumberSet: Boolean, contactCount: Int, vendorSettingsConfirmed: Boolean = false): List<ReadinessItem> = listOfNotNull(
        buildType(),
        smsPermissions(),
        notifications(),
        fullScreenIntent(),
        batteryOptimization(),
        vendorBackgroundSettings(vendorSettingsConfirmed),
        simForSms(),
        ReadinessItem(
            label = "Own phone number",
            ok = phoneNumberSet,
            detail = if (phoneNumberSet) "Advertised in your handshake QR" else "Enter it on the My QR tab",
            fix = if (phoneNumberSet) null else ReadinessFix.EnterPhoneNumber
        ),
        ReadinessItem(
            label = "Trusted contacts",
            ok = contactCount > 0,
            detail = if (contactCount > 0) "$contactCount paired" else "Scan a QR to pair a node",
            fix = if (contactCount > 0) null else ReadinessFix.PairContact
        )
    )

    // A debug build is signed with the public key in the repository, so anyone can build an
    // "update" that installs over it and inherits its data. Fine for testing, not for real use.
    // The certificate is checked, not only BuildConfig.DEBUG: a release variant signed with
    // the debug key is still a debug build as far as an attacker is concerned.
    private fun buildType(): ReadinessItem {
        val certificates = InstalledSignature.certificates(context)
        val debugSigned = certificates.isEmpty() || certificates.any(SigningIdentity::isDebugCertificate)
        val ok = !BuildConfig.DEBUG && !debugSigned
        val detail = when {
            BuildConfig.DEBUG -> "Debug build, for testing only. Install a release build you signed for real use."
            certificates.isEmpty() -> "Signing certificate unreadable. Treat as a test build."
            debugSigned -> "Release build signed with the public debug key. Anyone can replace it. Sign with your own key."
            else -> "Release build, certificate ${SigningIdentity.shortFingerprint(certificates.last())}"
        }
        return ReadinessItem(label = "Build", ok = ok, detail = detail, fix = null)
    }

    private fun smsPermissions(): ReadinessItem {
        val missing = SMS_PERMISSIONS.filterNot { granted(it) }
        return ReadinessItem(
            label = "SMS permissions",
            ok = missing.isEmpty(),
            detail = if (missing.isEmpty()) "Send and receive granted" else "Needed to send and receive alerts",
            fix = if (missing.isEmpty()) null else ReadinessFix.RequestPermissions(missing)
        )
    }

    private fun notifications(): ReadinessItem {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(Manifest.permission.POST_NOTIFICATIONS)
        val fix = when {
            permissionMissing -> ReadinessFix.RequestPermissions(listOf(Manifest.permission.POST_NOTIFICATIONS))
            !enabled -> ReadinessFix.OpenSettings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
            else -> null
        }
        return ReadinessItem(
            label = "Notifications",
            ok = enabled && !permissionMissing,
            detail = if (fix == null) "Enabled" else "The alarm rides on a notification",
            fix = fix
        )
    }

    private fun fullScreenIntent(): ReadinessItem {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val allowed = context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            return ReadinessItem(
                label = "Full-screen alerts",
                ok = allowed,
                detail = if (allowed) "Allowed" else "Android 14 blocks the lock-screen takeover until you allow it",
                fix = if (allowed) null else ReadinessFix.OpenSettings(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri())
                )
            )
        }
        return ReadinessItem("Full-screen alerts", true, "Granted at install on this Android version", null)
    }

    private fun batteryOptimization(): ReadinessItem {
        val ignoring = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
        return ReadinessItem(
            label = "Battery optimization",
            ok = ignoring,
            detail = if (ignoring) "Unrestricted" else "OEM battery savers kill the receiver in Doze",
            fix = if (ignoring) null else ReadinessFix.OpenSettings(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())
            )
        )
    }

    // Xiaomi, OPPO, realme, vivo, Huawei, HONOR, OnePlus and Samsung have a second layer of
    // background restrictions with their own screens. They cannot be read back, so the row
    // turns green only when the user says they have done it (T12). Absent on other vendors.
    private fun vendorBackgroundSettings(confirmed: Boolean): ReadinessItem? {
        val vendor = oemSettings.vendor ?: return null
        return ReadinessItem(
            label = "${vendor.label} background settings",
            ok = confirmed,
            detail = if (confirmed) "Confirmed by you. Re-check after a system update." else OemSettingsTable.instruction(vendor),
            fix = ReadinessFix.ConfirmVendorSettings(oemSettings.intentFor(vendor), OemSettingsTable.instruction(vendor))
        )
    }

    private fun simForSms(): ReadinessItem {
        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        val ready = subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return ReadinessItem(
            label = "SIM for SMS",
            ok = ready,
            detail = if (ready) "Default SMS SIM selected" else "No SIM is selected for sending SMS",
            fix = if (ready) null else ReadinessFix.OpenSettings(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        )
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun packageUri(): Uri = Uri.parse("package:${context.packageName}")

    private companion object {
        val SMS_PERMISSIONS = listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
    }
}
