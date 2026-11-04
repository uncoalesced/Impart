// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.prefs

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.uncoalesced.impart.core.security.LegacyPins
import com.uncoalesced.impart.core.security.LockoutState
import com.uncoalesced.impart.core.security.LockoutStore
import com.uncoalesced.impart.core.security.NodeStateStore
import com.uncoalesced.impart.core.security.PinRecord
import com.uncoalesced.impart.core.security.PinRecords
import com.uncoalesced.impart.core.security.PinStore
import com.uncoalesced.impart.domain.model.NodeIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// The single encrypted key-value store: node identity, the salted PIN hashes and lock
// counters, the dead man's switch schedule, the location toggle and the accepted legal
// version, all behind one Keystore-backed master key. No PIN is ever stored here, only
// the salt and keyed hash that PinVerifier checks against.
@Singleton
class SecurePrefsManager @Inject constructor(
    @ApplicationContext context: Context
) : PinStore, LockoutStore, NodeStateStore {
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
    override val hasCustomPins: StateFlow<Boolean> = _hasCustomPins.asStateFlow()

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

    override fun pinRecords(): PinRecords? {
        val standard = readRecord(KEY_STANDARD_SALT, KEY_STANDARD_MAC) ?: return null
        val duress = readRecord(KEY_DURESS_SALT, KEY_DURESS_MAC) ?: return null
        return PinRecords(standard, duress)
    }

    // Committed synchronously: the lock screen reads these back on the very next PIN.
    @SuppressLint("ApplySharedPref")
    override fun writePinRecords(records: PinRecords) {
        putRecords(prefs.edit(), records).putBoolean(KEY_CUSTOM_PINS, true).commit()
        _hasCustomPins.value = true
    }

    override fun legacyPlaintextPins(): LegacyPins? {
        val standard = prefs.getString(KEY_LEGACY_STANDARD_PIN, null) ?: return null
        val duress = prefs.getString(KEY_LEGACY_DURESS_PIN, null) ?: return null
        return LegacyPins(standard, duress)
    }

    // One commit: the hashes land and the plaintext leaves in the same write, so a crash
    // between the two cannot leave either both or neither.
    @SuppressLint("ApplySharedPref")
    override fun replaceLegacyPins(records: PinRecords) {
        putRecords(prefs.edit(), records)
            .remove(KEY_LEGACY_STANDARD_PIN)
            .remove(KEY_LEGACY_DURESS_PIN)
            .putBoolean(KEY_CUSTOM_PINS, true)
            .commit()
        _hasCustomPins.value = true
    }

    private fun putRecords(editor: SharedPreferences.Editor, records: PinRecords): SharedPreferences.Editor = editor
        .putString(KEY_STANDARD_SALT, encode(records.standard.salt))
        .putString(KEY_STANDARD_MAC, encode(records.standard.mac))
        .putString(KEY_DURESS_SALT, encode(records.duress.salt))
        .putString(KEY_DURESS_MAC, encode(records.duress.mac))

    private fun readRecord(saltKey: String, macKey: String): PinRecord? {
        val salt = prefs.getString(saltKey, null) ?: return null
        val mac = prefs.getString(macKey, null) ?: return null
        return PinRecord(decode(salt), decode(mac))
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    override fun lockoutState(): LockoutState? {
        if (!prefs.contains(KEY_FAILED_ATTEMPTS)) return null
        return LockoutState(
            failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            remainingMs = prefs.getLong(KEY_LOCKOUT_REMAINING_MS, 0L),
            elapsedAtWrite = prefs.getLong(KEY_LOCKOUT_ELAPSED_AT_WRITE, 0L),
            wallAtWrite = prefs.getLong(KEY_LOCKOUT_WALL_AT_WRITE, 0L),
            bootCount = prefs.getInt(KEY_LOCKOUT_BOOT_COUNT, 0)
        )
    }

    // Synchronous: the count must be on disk before the lock screen shows the answer, so a
    // force-stop straight after a wrong guess cannot forget it.
    @SuppressLint("ApplySharedPref")
    override fun writeLockoutState(state: LockoutState) {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, state.failedAttempts)
            .putLong(KEY_LOCKOUT_REMAINING_MS, state.remainingMs)
            .putLong(KEY_LOCKOUT_ELAPSED_AT_WRITE, state.elapsedAtWrite)
            .putLong(KEY_LOCKOUT_WALL_AT_WRITE, state.wallAtWrite)
            .putInt(KEY_LOCKOUT_BOOT_COUNT, state.bootCount)
            .commit()
    }

    // Only a correct standard PIN, the first PIN setup or the duress wipe reach this.
    @SuppressLint("ApplySharedPref")
    override fun clearLockout() {
        prefs.edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_REMAINING_MS)
            .remove(KEY_LOCKOUT_ELAPSED_AT_WRITE)
            .remove(KEY_LOCKOUT_WALL_AT_WRITE)
            .remove(KEY_LOCKOUT_BOOT_COUNT)
            .commit()
    }

    private val _vendorSettingsConfirmed = MutableStateFlow(prefs.getBoolean(KEY_VENDOR_SETTINGS_CONFIRMED, false))
    val vendorSettingsConfirmed: StateFlow<Boolean> = _vendorSettingsConfirmed.asStateFlow()

    fun setVendorSettingsConfirmed(confirmed: Boolean) {
        prefs.edit().putBoolean(KEY_VENDOR_SETTINGS_CONFIRMED, confirmed).apply()
        _vendorSettingsConfirmed.value = confirmed
    }

    private val _shufflePinPad = MutableStateFlow(prefs.getBoolean(KEY_SHUFFLE_PIN_PAD, false))
    val shufflePinPad: StateFlow<Boolean> = _shufflePinPad.asStateFlow()

    fun setShufflePinPad(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE_PIN_PAD, enabled).apply()
        _shufflePinPad.value = enabled
    }

    private val _wipeAfterFailedAttempts = MutableStateFlow(prefs.getBoolean(KEY_WIPE_AFTER_FAILURES, false))
    val wipeAfterFailedAttemptsFlow: StateFlow<Boolean> = _wipeAfterFailedAttempts.asStateFlow()

    override fun wipeAfterFailedAttempts(): Boolean = _wipeAfterFailedAttempts.value

    @SuppressLint("ApplySharedPref")
    fun setWipeAfterFailedAttempts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIPE_AFTER_FAILURES, enabled).commit()
        _wipeAfterFailedAttempts.value = enabled
    }

    fun acceptLegal(version: Int) {
        prefs.edit().putInt(KEY_LEGAL_VERSION, version).apply()
        _acceptedLegalVersion.value = version
    }

    override fun isNodeCorrupted(): Boolean = prefs.getBoolean(KEY_NODE_CORRUPTED, false)

    // Duress wipe: every stored value goes in one synchronous commit. The decoy flag that
    // survives is PlainDecoyFlagStore's; this file is deleted straight after by DuressWiper.
    @SuppressLint("ApplySharedPref")
    override fun wipeForDuress() {
        prefs.edit().clear().commit()
        _localPhoneNumber.value = null
        _locationSharingEnabled.value = false
        _hasCustomPins.value = false
        _acceptedLegalVersion.value = 0
        _wipeAfterFailedAttempts.value = false
        _shufflePinPad.value = false
        _vendorSettingsConfirmed.value = false
    }

    private fun readPhoneNumber(): String? =
        prefs.getString(KEY_LOCAL_PHONE, null)?.trim()?.takeIf { it.isNotBlank() }

    companion object {
        private const val PREFS_NAME = com.uncoalesced.impart.core.security.WipeInventory.SECURE_PREFS
        private const val KEY_LOCATION_SHARING = "location_sharing_enabled"
        private const val KEY_LOCAL_UUID = "local_node_uuid"
        private const val KEY_LOCAL_PHONE = "local_node_phone_number"
        private const val KEY_DMS_INTERVAL_HOURS = "dms_interval_hours"
        private const val KEY_DMS_LAST_CHECKIN = "dms_last_checkin"
        // Plaintext keys written by builds before T02. Read once by the migration, then removed.
        private const val KEY_LEGACY_STANDARD_PIN = "standard_pin"
        private const val KEY_LEGACY_DURESS_PIN = "duress_pin"
        private const val KEY_STANDARD_SALT = "standard_pin_salt"
        private const val KEY_STANDARD_MAC = "standard_pin_mac"
        private const val KEY_DURESS_SALT = "duress_pin_salt"
        private const val KEY_DURESS_MAC = "duress_pin_mac"
        private const val KEY_CUSTOM_PINS = "custom_pins_set"
        private const val KEY_FAILED_ATTEMPTS = "failed_unlock_attempts"
        private const val KEY_LOCKOUT_REMAINING_MS = "lockout_remaining_ms"
        private const val KEY_LOCKOUT_ELAPSED_AT_WRITE = "lockout_elapsed_at_write"
        private const val KEY_LOCKOUT_WALL_AT_WRITE = "lockout_wall_at_write"
        private const val KEY_LOCKOUT_BOOT_COUNT = "lockout_boot_count"
        private const val KEY_WIPE_AFTER_FAILURES = "wipe_after_failed_attempts"
        private const val KEY_SHUFFLE_PIN_PAD = "shuffle_pin_pad"
        private const val KEY_VENDOR_SETTINGS_CONFIRMED = "vendor_settings_confirmed"
        private const val KEY_LEGAL_VERSION = "accepted_legal_version"
        private const val KEY_NODE_CORRUPTED = "is_node_corrupted"
        private const val KEY_DB_PASSPHRASE = "database_passphrase"
        private const val DB_PASSPHRASE_BYTES = 32
    }
}
