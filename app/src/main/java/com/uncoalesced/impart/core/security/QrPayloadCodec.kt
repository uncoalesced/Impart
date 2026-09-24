// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.HandshakePayload
import org.json.JSONException
import org.json.JSONObject

object QrPayloadCodec {
    private const val KEY_UUID = "uuid"
    private const val KEY_PHONE = "phoneNumber"
    private const val KEY_PUBLIC_KEY = "publicKey"

    fun encode(payload: HandshakePayload): String = JSONObject()
        .put(KEY_UUID, payload.uuid)
        .put(KEY_PHONE, payload.phoneNumber)
        .put(KEY_PUBLIC_KEY, payload.publicKeyBase64)
        .toString()

    fun decode(jsonString: String): Result<HandshakePayload> {
        val json = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            return Result.failure(IllegalArgumentException("This QR code is not an Impart handshake.", e))
        }
        return try {
            Result.success(
                HandshakePayload(
                    uuid = json.getString(KEY_UUID),
                    phoneNumber = json.getString(KEY_PHONE),
                    publicKeyBase64 = json.getString(KEY_PUBLIC_KEY)
                )
            )
        } catch (e: JSONException) {
            Result.failure(IllegalArgumentException("Handshake QR is missing a required field.", e))
        }
    }
}
