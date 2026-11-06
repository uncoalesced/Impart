// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class OemSettingsResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val vendor: OemVendor? = OemSettingsTable.vendorFor(Build.MANUFACTURER, Build.BRAND)

    // The vendor screen if one resolves, else this app's details page. Never null on a known
    // vendor, so the readiness row always has somewhere to go.
    fun intentFor(vendor: OemVendor): Intent = OemSettingsPolicy.choose(
        candidates = OemSettingsTable.candidates(vendor),
        resolves = { candidate -> context.packageManager.resolveActivity(intent(candidate), 0) != null },
        toTarget = ::intent,
        fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )

    private fun intent(component: OemComponent): Intent =
        Intent().setComponent(ComponentName(component.packageName, component.className))
}
