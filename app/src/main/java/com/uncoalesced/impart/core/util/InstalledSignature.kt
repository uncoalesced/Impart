// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

// The DER certificates this install was signed with, oldest first. Empty if the package
// manager cannot say, which the readiness card treats as "not a release build".
object InstalledSignature {
    fun certificates(context: Context): List<ByteArray> = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return emptyList()
            val signatures = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
            signatures.orEmpty().map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures.orEmpty().map { it.toByteArray() }
        }
    } catch (e: PackageManager.NameNotFoundException) {
        emptyList()
    }
}
