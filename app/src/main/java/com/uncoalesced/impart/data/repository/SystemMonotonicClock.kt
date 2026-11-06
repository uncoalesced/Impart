// Engineered by uncoalesced
package com.uncoalesced.impart.data.repository

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.uncoalesced.impart.domain.repository.MonotonicClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SystemMonotonicClock @Inject constructor(
    @ApplicationContext private val context: Context
) : MonotonicClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}
