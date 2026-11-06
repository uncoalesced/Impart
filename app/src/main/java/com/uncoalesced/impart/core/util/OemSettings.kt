// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

// Vendors whose battery skins have a second layer of background restrictions (autostart,
// background pop-ups, "app launch" management) that the stock battery-optimisation exemption
// does not cover. The receiver that raises the alarm is killed by exactly these. The table
// follows dontkillmyapp.com and lives in code: nothing is fetched (T12).
enum class OemVendor(val label: String) {
    XIAOMI("Xiaomi / Redmi / POCO"),
    OPPO("OPPO"),
    REALME("realme"),
    VIVO("vivo / iQOO"),
    HUAWEI("Huawei"),
    HONOR("HONOR"),
    ONEPLUS("OnePlus"),
    SAMSUNG("Samsung")
}

// A settings screen as an explicit component. Candidates are tried in order; the first one
// the package manager can resolve is offered.
data class OemComponent(val packageName: String, val className: String)

object OemSettingsTable {
    fun vendorFor(manufacturer: String?, brand: String?): OemVendor? {
        val m = manufacturer.orEmpty().trim().lowercase()
        val b = brand.orEmpty().trim().lowercase()
        return when {
            m == "xiaomi" || b in setOf("xiaomi", "redmi", "poco") -> OemVendor.XIAOMI
            m == "realme" || b == "realme" -> OemVendor.REALME
            m == "oppo" || b == "oppo" -> OemVendor.OPPO
            m == "vivo" || b in setOf("vivo", "iqoo") -> OemVendor.VIVO
            m == "honor" || b == "honor" -> OemVendor.HONOR
            m == "huawei" || b == "huawei" -> OemVendor.HUAWEI
            m == "oneplus" || b == "oneplus" -> OemVendor.ONEPLUS
            m == "samsung" || b == "samsung" -> OemVendor.SAMSUNG
            else -> null
        }
    }

    fun candidates(vendor: OemVendor): List<OemComponent> = when (vendor) {
        OemVendor.XIAOMI -> listOf(
            OemComponent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            OemComponent("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
            OemComponent("com.miui.securitycenter", "com.miui.securityscan.MainActivity")
        )
        OemVendor.OPPO, OemVendor.REALME -> listOf(
            OemComponent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            OemComponent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            OemComponent("com.coloros.safecenter", "com.coloros.privacypermissionsentry.PermissionTopActivity"),
            OemComponent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            OemComponent("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
        )
        OemVendor.VIVO -> listOf(
            OemComponent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            OemComponent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            OemComponent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
        )
        OemVendor.HUAWEI, OemVendor.HONOR -> listOf(
            OemComponent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            OemComponent("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            OemComponent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        )
        OemVendor.ONEPLUS -> listOf(
            OemComponent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            OemComponent("com.android.settings", "com.android.settings.Settings\$BgOptimizeAppListActivity")
        )
        OemVendor.SAMSUNG -> listOf(
            OemComponent("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            OemComponent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            OemComponent("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
        )
    }

    // What the user has to do on that vendor's screen. Shown on the readiness row.
    fun instruction(vendor: OemVendor): String = when (vendor) {
        OemVendor.XIAOMI -> "Enable Autostart for Impart and set Battery saver to No restrictions."
        OemVendor.OPPO, OemVendor.REALME -> "Allow Impart to start automatically and to run in the background."
        OemVendor.VIVO -> "Allow Impart to start in the background and add it to the high background power consumption list."
        OemVendor.HUAWEI, OemVendor.HONOR -> "Set Impart to Manage manually with Auto-launch, Secondary launch and Run in background all on."
        OemVendor.ONEPLUS -> "Turn off battery optimisation for Impart and allow it to auto-launch."
        OemVendor.SAMSUNG -> "Put Impart under Never sleeping apps and keep Adaptive battery from restricting it."
    }
}

// Pure choice: the first candidate the device can resolve, otherwise the fallback the caller
// supplies (the app's own details page, which every Android has).
object OemSettingsPolicy {
    fun <T> choose(candidates: List<OemComponent>, resolves: (OemComponent) -> Boolean, toTarget: (OemComponent) -> T, fallback: T): T {
        for (candidate in candidates) {
            if (resolves(candidate)) return toTarget(candidate)
        }
        return fallback
    }
}
