// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload
import org.json.JSONException
import org.json.JSONObject

// Every Impart SMS body is this prefix followed by base64 Tink ciphertext.
const val IMPART_ALERT_PREFIX = "IMPART_ALERT:"

// Wire format of the decrypted body. Keys are frozen: nodes on older builds still page us.
object PanicPayloadCodec {
    private const val KEY_TYPE = "type"
    private const val KEY_URGENCY = "urgency"
    private const val KEY_SENDER_PHONE = "senderPhoneNumber"
    private const val KEY_SENDER_KEY = "senderPublicKey"
    private const val KEY_LAT = "lat"
    private const val KEY_LNG = "lng"

    private const val TYPE_PANIC = "PANIC"
    private const val TYPE_ACK = "ACK"
    private const val URGENCY_CRITICAL = "CRITICAL"

    fun encode(payload: PanicPayload): String = when (payload) {
        is PanicPayload.Panic -> JSONObject().apply {
            put(KEY_TYPE, TYPE_PANIC)
            put(KEY_URGENCY, URGENCY_CRITICAL)
            payload.senderPhoneNumber?.takeIf { it.isNotBlank() }?.let { put(KEY_SENDER_PHONE, it) }
            put(KEY_SENDER_KEY, payload.senderPublicKeyBase64)
            if (payload.lat != null && payload.lng != null) {
                put(KEY_LAT, payload.lat)
                put(KEY_LNG, payload.lng)
            }
        }.toString()

        PanicPayload.Ack -> JSONObject().put(KEY_TYPE, TYPE_ACK).toString()
    }

    fun decode(plaintext: String): Result<PanicPayload> {
        val json = try {
            JSONObject(plaintext)
        } catch (e: JSONException) {
            return Result.failure(IllegalArgumentException("Decrypted alert is not JSON.", e))
        }
        return when (val type = json.optString(KEY_TYPE, TYPE_PANIC)) {
            TYPE_ACK -> Result.success(PanicPayload.Ack)
            TYPE_PANIC -> {
                val senderKey = json.optString(KEY_SENDER_KEY)
                if (senderKey.isBlank()) {
                    return Result.failure(IllegalArgumentException("Panic payload carries no sender public key."))
                }
                val hasCoordinates = json.has(KEY_LAT) && json.has(KEY_LNG)
                Result.success(
                    PanicPayload.Panic(
                        senderPhoneNumber = json.optString(KEY_SENDER_PHONE).takeIf { it.isNotBlank() },
                        senderPublicKeyBase64 = senderKey,
                        lat = if (hasCoordinates) json.getDouble(KEY_LAT) else null,
                        lng = if (hasCoordinates) json.getDouble(KEY_LNG) else null
                    )
                )
            }
            else -> Result.failure(IllegalArgumentException("Unknown alert type: $type"))
        }
    }
}
