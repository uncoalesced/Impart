// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.prefs

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.uncoalesced.impart.domain.model.NodeIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// The single encrypted key-value store: node identity, PINs and lock counters, the dead
// man's switch schedule, the location toggle and the accepted legal version, all behind one
// Keystore-backed master key.
@Singleton
class SecurePrefsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _localPhoneNumber = MutableStateFlow(readPhoneNumber())

    // This node's own number, typed in on the My QR screen and validated there. Android
    // cannot read it from the SIM reliably, so the user is the source of truth.
    val localPhoneNumber: StateFlow<String?> = _localPhoneNumber.asStateFlow()

    private val _locationSharingEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOCATION_SHARING, false))
    val locationSharingEnabled: StateFlow<Boolean> = _locationSharingEnabled.asStateFlow()

    private val _hasCustomPins = MutableStateFlow(prefs.getBoolean(KEY_CUSTOM_PINS, false))

    // False until the user has chosen their own PINs. The app refuses to go past the PIN
    // setup screen until then, so the public defaults never guard anything.
    val hasCustomPins: StateFlow<Boolean> = _hasCustomPins.asStateFlow()

    private val _acceptedLegalVersion = MutableStateFlow(prefs.getInt(KEY_LEGAL_VERSION, 0))
    val acceptedLegalVersion: StateFlow<Int> = _acceptedLegalVersion.asStateFlow()

    fun setLocationSharingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCATION_SHARING, enabled).apply()
        _locationSharingEnabled.value = enabled
    }

    // Generated once so the handshake QR and every alert advertise the same UUID forever.
    fun localUuid(): String {
        prefs.getString(KEY_LOCAL_UUID, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_LOCAL_UUID, generated).apply()
        return generated
    }

    fun hasDatabasePassphrase(): Boolean = prefs.contains(KEY_DB_PASSPHRASE)

    // SQLCipher key for the Room database. Lives here so the duress wipe's clear() destroys
    // it with everything else, leaving any surviving database file unreadable. Committed
    // synchronously because the database is created with it straight after.
    @SuppressLint("ApplySharedPref")
    fun databasePassphrase(): ByteArray {
        prefs.getString(KEY_DB_PASSPHRASE, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val generated = ByteArray(DB_PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB_PASSPHRASE, Base64.encodeToString(generated, Base64.NO_WRAP)).commit()
        return generated
    }

    fun setLocalPhoneNumber(number: String) {
        val trimmed = number.trim()
        prefs.edit().putString(KEY_LOCAL_PHONE, trimmed).apply()
        _localPhoneNumber.value = trimmed.takeIf { it.isNotBlank() }
    }

    fun nodeIdentity(): Result<NodeIdentity> {
        val phone = _localPhoneNumber.value
            ?: return Result.failure(
                IllegalStateException("No phone number set for this node. Enter it on the My QR screen.")
            )
        return Result.success(NodeIdentity(localUuid(), phone))
    }

    fun dmsIntervalHours(): Int = prefs.getInt(KEY_DMS_INTERVAL_HOURS, 0)

    fun setDmsIntervalHours(hours: Int) {
        prefs.edit().putInt(KEY_DMS_INTERVAL_HOURS, hours).apply()
    }

    fun dmsLastCheckin(): Long = prefs.getLong(KEY_DMS_LAST_CHECKIN, 0L)

    fun setDmsLastCheckin(epochMillis: Long) {
        prefs.edit().putLong(KEY_DMS_LAST_CHECKIN, epochMillis).apply()
    }

    fun standardPin(): String = prefs.getString(KEY_STANDARD_PIN, null) ?: DEFAULT_STANDARD_PIN

    fun duressPin(): String = prefs.getString(KEY_DURESS_PIN, null) ?: DEFAULT_DURESS_PIN

    fun setPins(standardPin: String, duressPin: String) {
        prefs.edit()
            .putString(KEY_STANDARD_PIN, standardPin)
            .putString(KEY_DURESS_PIN, duressPin)
            .putBoolean(KEY_CUSTOM_PINS, true)
            .apply()
        _hasCustomPins.value = true
    }

    fun failedUnlockAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    fun lockedUntil(): Long = prefs.getLong(KEY_LOCKED_UNTIL, 0L)

    fun recordUnlockFailure(attempts: Int, lockedUntil: Long) {
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).putLong(KEY_LOCKED_UNTIL, lockedUntil).apply()
    }

    fun clearUnlockFailures() {
        prefs.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCKED_UNTIL).apply()
    }

    fun acceptLegal(version: Int) {
        prefs.edit().putInt(KEY_LEGAL_VERSION, version).apply()
        _acceptedLegalVersion.value = version
    }

    fun isNodeCorrupted(): Boolean = prefs.getBoolean(KEY_NODE_CORRUPTED, false)

    // Duress wipe: every stored value goes, the decoy flag is set, in one synchronous commit,
    // so a crash straight after still leaves the node in decoy mode.
    @SuppressLint("ApplySharedPref")
    fun wipeForDuress() {
        prefs.edit().clear().putBoolean(KEY_NODE_CORRUPTED, true).commit()
        _localPhoneNumber.value = null
        _locationSharingEnabled.value = false
        _hasCustomPins.value = false
        _acceptedLegalVersion.value = 0
    }

    private fun readPhoneNumber(): String? =
        prefs.getString(KEY_LOCAL_PHONE, null)?.trim()?.takeIf { it.isNotBlank() }

    companion object {
        private const val PREFS_NAME = "impart_node_secure_prefs"
        private const val KEY_LOCATION_SHARING = "location_sharing_enabled"
        private const val KEY_LOCAL_UUID = "local_node_uuid"
        private const val KEY_LOCAL_PHONE = "local_node_phone_number"
        private const val KEY_DMS_INTERVAL_HOURS = "dms_interval_hours"
        private const val KEY_DMS_LAST_CHECKIN = "dms_last_checkin"
        private const val KEY_STANDARD_PIN = "standard_pin"
        private const val KEY_DURESS_PIN = "duress_pin"
        private const val KEY_CUSTOM_PINS = "custom_pins_set"
        private const val KEY_FAILED_ATTEMPTS = "failed_unlock_attempts"
        private const val KEY_LOCKED_UNTIL = "unlock_locked_until"
        private const val KEY_LEGAL_VERSION = "accepted_legal_version"
        private const val KEY_NODE_CORRUPTED = "is_node_corrupted"
        private const val KEY_DB_PASSPHRASE = "database_passphrase"
        private const val DB_PASSPHRASE_BYTES = 32
        const val DEFAULT_STANDARD_PIN = "1234"
        const val DEFAULT_DURESS_PIN = "9999"
    }
}
