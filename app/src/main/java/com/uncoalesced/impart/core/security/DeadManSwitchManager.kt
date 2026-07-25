// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.*
import com.uncoalesced.impart.core.workers.DeadMansSwitchWorker
import com.uncoalesced.impart.core.workers.WarningNotificationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeadManSwitchManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val PREFS_NAME = "impart_dms_secure_prefs"
    private val KEY_INTERVAL_HOURS = "dms_interval_hours"
    private val KEY_LAST_CHECKIN = "dms_last_checkin"
    
    private val DMS_WORK_NAME = "impart_dms_worker"
    private val WARNING_WORK_NAME = "impart_dms_warning_worker"

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val workManager = WorkManager.getInstance(context)

    fun getIntervalHours(): Int {
        return securePrefs.getInt(KEY_INTERVAL_HOURS, 0) // 0 means Off
    }

    fun setIntervalHours(hours: Int) {
        securePrefs.edit().putInt(KEY_INTERVAL_HOURS, hours).apply()
        if (hours == 0) {
            cancelDeadMansSwitch()
        } else {
            checkIn()
        }
    }

    fun getLastCheckin(): Long {
        return securePrefs.getLong(KEY_LAST_CHECKIN, 0L)
    }

    fun checkIn() {
        val hours = getIntervalHours()
        if (hours == 0) return

        val now = System.currentTimeMillis()
        securePrefs.edit().putLong(KEY_LAST_CHECKIN, now).apply()

        scheduleWork(hours)
    }

    private fun scheduleWork(hours: Int) {
        workManager.cancelUniqueWork(DMS_WORK_NAME)
        workManager.cancelUniqueWork(WARNING_WORK_NAME)

        // Schedule Main Panic Dispatch
        val panicDelayMinutes = hours * 60L
        val panicWorkRequest = OneTimeWorkRequestBuilder<DeadMansSwitchWorker>()
            .setInitialDelay(panicDelayMinutes, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            DMS_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            panicWorkRequest
        )

        // Schedule Warning Notification (1 hour before expiration)
        if (panicDelayMinutes > 60L) {
            val warningDelayMinutes = panicDelayMinutes - 60L
            val warningWorkRequest = OneTimeWorkRequestBuilder<WarningNotificationWorker>()
                .setInitialDelay(warningDelayMinutes, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniqueWork(
                WARNING_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                warningWorkRequest
            )
        }
    }

    fun cancelDeadMansSwitch() {
        securePrefs.edit().putLong(KEY_LAST_CHECKIN, 0L).apply()
        workManager.cancelUniqueWork(DMS_WORK_NAME)
        workManager.cancelUniqueWork(WARNING_WORK_NAME)
    }
}
