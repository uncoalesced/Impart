// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.uncoalesced.impart.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// PackageManager is the source of truth for which launcher entry is live; nothing is
// mirrored into preferences.
@Singleton
class CloakManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager = context.packageManager
    private val mainComponent = ComponentName(context, MainActivity::class.java)
    private val aliasComponent = ComponentName(context, "${context.packageName}$ALIAS_SUFFIX")

    private val _isCloaked = MutableStateFlow(readCloaked())
    val isCloaked: StateFlow<Boolean> = _isCloaked.asStateFlow()

    fun setCloakEnabled(enabled: Boolean) {
        val show = if (enabled) aliasComponent else mainComponent
        val hide = if (enabled) mainComponent else aliasComponent
        // Enable the new entry before disabling the old one so the app never vanishes from
        // the launcher entirely.
        packageManager.setComponentEnabledSetting(
            show, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(
            hide, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
        )
        _isCloaked.value = readCloaked()
    }

    private fun readCloaked(): Boolean =
        packageManager.getComponentEnabledSetting(aliasComponent) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private companion object {
        const val ALIAS_SUFFIX = ".MainActivityAlias"
    }
}
