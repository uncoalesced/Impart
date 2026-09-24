// Engineered by uncoalesced
package com.uncoalesced.impart.core.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uncoalesced.impart.core.services.ImpartNotifications
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.domain.repository.RelayManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class DeadMansSwitchWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val contactRepository: ContactRepository,
    private val relayManager: RelayManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val contacts = contactRepository.getActiveContacts().first()
            if (contacts.isEmpty()) {
                Log.w(TAG, "Dead man's switch expired with no trusted contacts; nothing dispatched")
                notify("DEAD MAN'S SWITCH EXPIRED", "No trusted contacts were paired, so no panic was sent.")
                return Result.success()
            }
            val report = relayManager.broadcastPanic(contacts)
            if (report.allSent) {
                Log.i(TAG, "Dead man's switch dispatched to ${report.sent} nodes")
                notify("DEAD MAN'S SWITCH FIRED", "Panic dispatched to ${report.sent} trusted nodes.")
            } else {
                Log.e(TAG, "Dead man's switch partial failure: ${report.failed.size} of ${report.attempted} nodes failed")
                notify(
                    "DEAD MAN'S SWITCH PARTIAL FAILURE",
                    "Sent to ${report.sent} of ${report.attempted} nodes. Failed: ${report.failed.joinToString()}"
                )
            }
            // Retrying would page the nodes that already received it, so the work is done either way.
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Dead man's switch dispatch crashed", e)
            notify("DEAD MAN'S SWITCH FAILED", "Panic could not be dispatched: ${e.message ?: e.javaClass.simpleName}")
            Result.failure()
        }
    }

    private fun notify(title: String, text: String) {
        ImpartNotifications.notifyHeadsUp(
            context, ImpartNotifications.CHANNEL_STATUS, ImpartNotifications.ID_DMS_REPORT, title, text
        )
    }

    private companion object {
        const val TAG = "DeadMansSwitchWorker"
    }
}
