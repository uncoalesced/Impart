// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class PinVerificationResult {
    STANDARD_SUCCESS,
    DURESS_TRIGGERED,
    INVALID_PIN
}

@Singleton
class DuressManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreHelper: KeystoreHelper
) {
    private val PREFS_NAME = "impart_security_prefs"
    private val KEY_STANDARD_PIN = "standard_pin"
    private val KEY_DURESS_PIN = "duress_pin"
    private val KEY_IS_CORRUPTED = "is_node_corrupted"

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStandardPin(): String {
        return prefs.getString(KEY_STANDARD_PIN, "1234") ?: "1234"
    }

    fun getDuressPin(): String {
        return prefs.getString(KEY_DURESS_PIN, "9999") ?: "9999"
    }

    fun updatePins(standardPin: String, duressPin: String) {
        prefs.edit()
            .putString(KEY_STANDARD_PIN, standardPin)
            .putString(KEY_DURESS_PIN, duressPin)
            .apply()
    }

    fun isNodeCorrupted(): Boolean {
        return prefs.getBoolean(KEY_IS_CORRUPTED, false)
    }

    fun verifyPin(enteredPin: String): PinVerificationResult {
        if (isNodeCorrupted()) {
            return PinVerificationResult.DURESS_TRIGGERED
        }

        val standardPin = getStandardPin()
        val duressPin = getDuressPin()

        return when (enteredPin) {
            standardPin -> PinVerificationResult.STANDARD_SUCCESS
            duressPin -> {
                executeNuclearWipe()
                PinVerificationResult.DURESS_TRIGGERED
            }
            else -> PinVerificationResult.INVALID_PIN
        }
    }

    fun executeNuclearWipe() {
        // 1. Mark state as corrupted permanently
        prefs.edit().putBoolean(KEY_IS_CORRUPTED, true).apply()

        // 2. Purge Room Database files
        try {
            context.deleteDatabase("impart_database")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Purge Tink E2EE Keystore
        keystoreHelper.purgeKeys()
    }
}
