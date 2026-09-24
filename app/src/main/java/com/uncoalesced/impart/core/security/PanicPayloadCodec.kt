// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload
import org.json.JSONException
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

// Every Impart SMS body is this prefix followed by base64 Tink ciphertext.
const val IMPART_ALERT_PREFIX = "IMPART_ALERT:"

// Decrypted alert from a build that predates signed alerts. Rejected, never trusted.
class OutdatedAlertException : IllegalArgumentException("Alert uses the unsigned v1 format.")

// The signature and the exact payload bytes it covers. The payload string is carried as-is
// so the receiver verifies the same bytes the sender signed; nothing is re-serialized.
data class SignedAlert(val signatureBase64: String, val payloadJson: String)

// Wire format v2. Short keys keep a worst-case panic inside four SMS parts.
object PanicPayloadCodec {
    const val VERSION = 2
    private const val SEPARATOR = '.'
    private const val KEY_VERSION = "v"
    private const val KEY_TYPE = "t"
    private const val KEY_FROM = "from"
    private const val KEY_TO = "to"
    private const val KEY_ID = "id"
    private const val KEY_SENT_AT = "ts"
    private const val KEY_LAT = "lat"
    private const val KEY_LNG = "lng"
    private const val KEY_ACK_FOR = "ack"
    private const val TYPE_PANIC = "P"
    private const val TYPE_ACK = "A"
    private const val ID_BYTES = 16

    private val random = SecureRandom()
    private val idEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun newMessageId(): String = ByteArray(ID_BYTES).also { random.nextBytes(it) }.let(idEncoder::encodeToString)

    fun encode(payload: PanicPayload): String {
        val json = JSONObject()
            .put(KEY_VERSION, VERSION)
            .put(KEY_TYPE, if (payload is PanicPayload.Ack) TYPE_ACK else TYPE_PANIC)
            .put(KEY_FROM, payload.from)
            .put(KEY_TO, payload.to)
            .put(KEY_ID, payload.id)
            .put(KEY_SENT_AT, payload.sentAt)
        when (payload) {
            is PanicPayload.Panic -> if (payload.lat != null && payload.lng != null) {
                json.put(KEY_LAT, payload.lat).put(KEY_LNG, payload.lng)
            }
            is PanicPayload.Ack -> json.put(KEY_ACK_FOR, payload.ackFor)
        }
        return json.toString()
    }

    fun decode(payloadJson: String): Result<PanicPayload> {
        val json = try {
            JSONObject(payloadJson)
        } catch (e: JSONException) {
            return Result.failure(IllegalArgumentException("Alert payload is not JSON.", e))
        }
        if (json.optInt(KEY_VERSION, 1) < VERSION) return Result.failure(OutdatedAlertException())
        return try {
            val from = json.getString(KEY_FROM)
            val to = json.getString(KEY_TO)
            val id = json.getString(KEY_ID)
            val sentAt = json.getLong(KEY_SENT_AT)
            if (from.isBlank() || to.isBlank() || id.isBlank()) {
                return Result.failure(IllegalArgumentException("Alert payload has a blank identity field."))
            }
            when (val type = json.getString(KEY_TYPE)) {
                TYPE_PANIC -> {
                    val hasCoordinates = json.has(KEY_LAT) && json.has(KEY_LNG)
                    Result.success(
                        PanicPayload.Panic(
                            from = from,
                            to = to,
                            id = id,
                            sentAt = sentAt,
                            lat = if (hasCoordinates) json.getDouble(KEY_LAT) else null,
                            lng = if (hasCoordinates) json.getDouble(KEY_LNG) else null
                        )
                    )
                }
                TYPE_ACK -> Result.success(PanicPayload.Ack(from, to, id, sentAt, json.getString(KEY_ACK_FOR)))
                else -> Result.failure(IllegalArgumentException("Unknown alert type: $type"))
            }
        } catch (e: JSONException) {
            Result.failure(IllegalArgumentException("Alert payload is missing a required field.", e))
        }
    }

    fun seal(signed: SignedAlert): String = signed.signatureBase64 + SEPARATOR + signed.payloadJson

    // A v1 plaintext is bare JSON. A v2 plaintext is "<base64 signature>.<json>"; base64
    // never contains '.', so the first '.' is always the separator.
    fun unseal(plaintext: String): Result<SignedAlert> {
        if (plaintext.trimStart().startsWith("{")) return Result.failure(OutdatedAlertException())
        val cut = plaintext.indexOf(SEPARATOR)
        if (cut <= 0 || cut == plaintext.lastIndex) {
            return Result.failure(IllegalArgumentException("Alert is not a signed envelope."))
        }
        return Result.success(SignedAlert(plaintext.substring(0, cut), plaintext.substring(cut + 1)))
    }
}
