// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val PREFS_NAME = "impart_node_secure_prefs"
    private val KEY_LOCATION_SHARING = "location_sharing_enabled"
    private val KEY_LOCAL_UUID = "local_node_uuid"
    private val KEY_LOCAL_PHONE = "local_node_phone_number"

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isLocationSharingEnabled(): Boolean =
        securePrefs.getBoolean(KEY_LOCATION_SHARING, false)

    fun setLocationSharingEnabled(enabled: Boolean) {
        securePrefs.edit().putBoolean(KEY_LOCATION_SHARING, enabled).apply()
    }

    // Stable node identity. Generated once on first read so a handshake QR and a
    // panic dispatch always advertise the same UUID across process restarts.
    fun getOrCreateLocalUuid(): String {
        securePrefs.getString(KEY_LOCAL_UUID, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        securePrefs.edit().putString(KEY_LOCAL_UUID, generated).apply()
        return generated
    }

    // This node's own phone number, advertised in the handshake QR so counterparts
    // know where to send the SMS panic. Set manually because TelephonyManager cannot
    // reliably read a device's own number on modern Android / most carriers.
    fun getLocalPhoneNumber(): String? = securePrefs.getString(KEY_LOCAL_PHONE, null)

    fun setLocalPhoneNumber(number: String) {
        securePrefs.edit().putString(KEY_LOCAL_PHONE, number).apply()
    }
}
