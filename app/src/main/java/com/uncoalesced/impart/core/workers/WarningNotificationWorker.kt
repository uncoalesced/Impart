// Engineered by uncoalesced
package com.uncoalesced.impart.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uncoalesced.impart.core.services.ImpartNotifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WarningNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        ImpartNotifications.notifyHeadsUp(
            context,
            ImpartNotifications.CHANNEL_STATUS,
            ImpartNotifications.ID_DMS_WARNING,
            "DEAD MAN'S SWITCH WARNING",
            "Your check-in expires in 1 hour. Open Impart and check in, or a panic goes out to every trusted node."
        )
        return Result.success()
    }
}
