// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.uncoalesced.impart.core.workers.DeadMansSwitchWorker
import com.uncoalesced.impart.core.workers.WarningNotificationWorker
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeadManSwitchManager @Inject constructor(
    @ApplicationContext context: Context,
    private val securePrefsManager: SecurePrefsManager
) {
    private val workManager = WorkManager.getInstance(context)

    private val _intervalHours = MutableStateFlow(securePrefsManager.dmsIntervalHours())
    val intervalHours: StateFlow<Int> = _intervalHours.asStateFlow()

    private val _lastCheckin = MutableStateFlow(securePrefsManager.dmsLastCheckin())
    val lastCheckin: StateFlow<Long> = _lastCheckin.asStateFlow()

    fun setIntervalHours(hours: Int) {
        securePrefsManager.setDmsIntervalHours(hours)
        _intervalHours.value = hours
        if (hours == INTERVAL_OFF) cancel() else checkIn()
    }

    fun checkIn() {
        val hours = _intervalHours.value
        if (hours == INTERVAL_OFF) return
        val now = System.currentTimeMillis()
        securePrefsManager.setDmsLastCheckin(now)
        _lastCheckin.value = now
        schedule(hours)
    }

    fun cancel() {
        securePrefsManager.setDmsLastCheckin(0L)
        _lastCheckin.value = 0L
        workManager.cancelUniqueWork(DMS_WORK_NAME)
        workManager.cancelUniqueWork(WARNING_WORK_NAME)
    }

    private fun schedule(hours: Int) {
        val panicDelayMinutes = hours * MINUTES_PER_HOUR
        workManager.enqueueUniqueWork(
            DMS_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DeadMansSwitchWorker>()
                .setInitialDelay(panicDelayMinutes, TimeUnit.MINUTES)
                .build()
        )

        val warningDelayMinutes = panicDelayMinutes - WARNING_LEAD_MINUTES
        if (warningDelayMinutes > 0L) {
            workManager.enqueueUniqueWork(
                WARNING_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WarningNotificationWorker>()
                    .setInitialDelay(warningDelayMinutes, TimeUnit.MINUTES)
                    .build()
            )
        } else {
            workManager.cancelUniqueWork(WARNING_WORK_NAME)
        }
    }

    companion object {
        const val INTERVAL_OFF = 0
        private const val MINUTES_PER_HOUR = 60L
        private const val WARNING_LEAD_MINUTES = 60L
        private const val DMS_WORK_NAME = "impart_dms_worker"
        private const val WARNING_WORK_NAME = "impart_dms_warning_worker"
    }
}
