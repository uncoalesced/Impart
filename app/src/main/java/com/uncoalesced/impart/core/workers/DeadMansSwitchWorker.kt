// Engineered by uncoalesced
package com.uncoalesced.impart.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.domain.repository.RelayManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull

@HiltWorker
class DeadMansSwitchWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val contactRepository: ContactRepository,
    private val relayManager: RelayManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val contacts = contactRepository.getActiveContacts().firstOrNull() ?: emptyList()
            if (contacts.isEmpty()) {
                return@coroutineScope Result.success()
            }

            val deferreds = contacts.map { contact ->
                async {
                    relayManager.dispatchPanic(
                        targetFcmToken = contact.fcmToken,
                        counterpartPublicKeyBase64 = contact.publicKeyBase64,
                        targetPhoneNumber = contact.phoneNumber,
                        lat = null,
                        lng = null
                    )
                }
            }

            deferreds.awaitAll()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
