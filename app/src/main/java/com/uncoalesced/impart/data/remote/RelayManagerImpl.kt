// Engineered by uncoalesced
package com.uncoalesced.impart.data.remote

import android.telephony.SmsManager
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.util.NodeIdentityHelper
import com.uncoalesced.impart.domain.repository.RelayManager
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS is the only transport. Every panic/ack is Tink-encrypted client-side and sent as an
 * IMPART_ALERT SMS to the counterpart's phone number. No network relay, no Google infrastructure.
 */
@Singleton
class RelayManagerImpl @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val nodeIdentityHelper: NodeIdentityHelper
) : RelayManager {

    override suspend fun dispatchPanic(
        targetPhoneNumber: String?,
        counterpartPublicKeyBase64: String,
        lat: Double?,
        lng: Double?
    ): Result<Unit> {
        return try {
            val myPhoneNumber = nodeIdentityHelper.localPhoneNumber()
            val myPublicKey = cryptoManager.getPublicKey()

            val payloadObj = JSONObject().apply {
                put("type", "PANIC")
                put("urgency", "CRITICAL")
                // So the receiver can send an encrypted ACK back over SMS.
                if (!myPhoneNumber.isNullOrBlank()) put("senderPhoneNumber", myPhoneNumber)
                put("senderPublicKey", myPublicKey)
                if (lat != null && lng != null) {
                    put("lat", lat)
                    put("lng", lng)
                }
            }
            val ciphertext = cryptoManager.encrypt(payloadObj.toString(), counterpartPublicKeyBase64)
            dispatchSms(targetPhoneNumber, ciphertext)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun dispatchAck(
        targetPhoneNumber: String?,
        counterpartPublicKeyBase64: String
    ): Result<Unit> {
        return try {
            val payloadObj = JSONObject().apply {
                put("type", "ACK")
            }
            val ciphertext = cryptoManager.encrypt(payloadObj.toString(), counterpartPublicKeyBase64)
            dispatchSms(targetPhoneNumber, ciphertext)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    private fun dispatchSms(phoneNumber: String?, ciphertext: String): Result<Unit> {
        if (phoneNumber.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No phone number available for SMS dispatch."))
        }

        return try {
            val smsManager = SmsManager.getDefault()
            val message = "IMPART_ALERT:$ciphertext"

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
