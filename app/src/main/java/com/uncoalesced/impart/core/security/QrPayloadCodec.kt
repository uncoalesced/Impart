// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.HandshakePayload
import org.json.JSONException
import org.json.JSONObject

object QrPayloadCodec {
    const val VERSION = 2
    private const val KEY_VERSION = "v"
    private const val KEY_UUID = "uuid"
    private const val KEY_PHONE = "phoneNumber"
    private const val KEY_PUBLIC_KEY = "publicKey"
    private const val KEY_SIGNING_KEY = "signingKey"

    fun encode(payload: HandshakePayload): String = JSONObject()
        .put(KEY_VERSION, VERSION)
        .put(KEY_UUID, payload.uuid)
        .put(KEY_PHONE, payload.phoneNumber)
        .put(KEY_PUBLIC_KEY, payload.publicKeyBase64)
        .put(KEY_SIGNING_KEY, payload.signingKeyBase64)
        .toString()

    fun decode(jsonString: String): Result<HandshakePayload> {
        val json = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            return Result.failure(IllegalArgumentException("This QR code is not an Impart handshake.", e))
        }
        if (json.optInt(KEY_VERSION, 1) < VERSION || !json.has(KEY_SIGNING_KEY)) {
            return Result.failure(
                IllegalArgumentException("This QR is from an older Impart version. Update both phones and pair again.")
            )
        }
        return try {
            Result.success(
                HandshakePayload(
                    uuid = json.getString(KEY_UUID),
                    phoneNumber = json.getString(KEY_PHONE),
                    publicKeyBase64 = json.getString(KEY_PUBLIC_KEY),
                    signingKeyBase64 = json.getString(KEY_SIGNING_KEY)
                )
            )
        } catch (e: JSONException) {
            Result.failure(IllegalArgumentException("Handshake QR is missing a required field.", e))
        }
    }
}
