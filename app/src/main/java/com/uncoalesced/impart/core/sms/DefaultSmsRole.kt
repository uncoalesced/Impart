// Engineered by uncoalesced
package com.uncoalesced.impart.core.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// How the role is asked for on each Android version. Pure, so the branch is unit-tested.
enum class RoleRequestStrategy {
    // API 29 and later: RoleManager.createRequestRoleIntent(ROLE_SMS).
    ROLE_MANAGER,
    // API 26 to 28: Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT with the package name.
    CHANGE_DEFAULT_INTENT;

    companion object {
        fun forApi(apiLevel: Int): RoleRequestStrategy =
            if (apiLevel >= 29) ROLE_MANAGER else CHANGE_DEFAULT_INTENT
    }
}

// Optional default SMS app mode (T14). As the default, Impart receives SMS_DELIVER first and
// never writes an alert to the Telephony provider, so no inbox copy exists (A4-1). The
// system is the source of truth for who holds the role; nothing is mirrored into prefs.
class DefaultSmsRole @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isDefault(): Boolean = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    // The intent that asks the user to make Impart the default, or null when it already is.
    fun requestIntent(): Intent? {
        if (isDefault()) return null
        // The explicit SDK_INT check is what lint needs; RoleRequestStrategy is the tested rule.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            RoleRequestStrategy.forApi(Build.VERSION.SDK_INT) == RoleRequestStrategy.ROLE_MANAGER
        ) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }

    // Android has no API to give a role back. The user picks another app in system settings.
    fun manageDefaultAppsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}
