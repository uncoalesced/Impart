// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.uncoalesced.impart.data.local.db.ImpartDatabase
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val securePrefsManager: SecurePrefsManager,
    private val keystoreHelper: KeystoreHelper
) {
    private val _isCorrupted = MutableStateFlow(securePrefsManager.isNodeCorrupted())

    // Observed by MainActivity: the moment this flips the whole UI becomes the decoy.
    val isCorrupted: StateFlow<Boolean> = _isCorrupted.asStateFlow()

    fun verifyPin(enteredPin: String): PinVerificationResult {
        if (_isCorrupted.value) return PinVerificationResult.DURESS_TRIGGERED
        // Until the user has chosen PINs, the public defaults must not unlock or wipe anything.
        if (!securePrefsManager.hasCustomPins.value) return PinVerificationResult.INVALID_PIN
        return when (enteredPin) {
            securePrefsManager.standardPin() -> PinVerificationResult.STANDARD_SUCCESS
            securePrefsManager.duressPin() -> {
                executeNuclearWipe()
                PinVerificationResult.DURESS_TRIGGERED
            }
            else -> PinVerificationResult.INVALID_PIN
        }
    }

    fun updatePins(standardPin: String, duressPin: String): Result<Unit> {
        if (!isWellFormed(standardPin) || !isWellFormed(duressPin)) {
            return Result.failure(
                IllegalArgumentException("PINs must be $MIN_PIN_LENGTH to $MAX_PIN_LENGTH digits.")
            )
        }
        if (standardPin == duressPin) {
            return Result.failure(IllegalArgumentException("The duress PIN must differ from the standard PIN."))
        }
        if (standardPin in PUBLIC_DEFAULTS || duressPin in PUBLIC_DEFAULTS) {
            return Result.failure(IllegalArgumentException("1234 and 9999 are the published defaults. Choose other PINs."))
        }
        securePrefsManager.setPins(standardPin, duressPin)
        return Result.success(Unit)
    }

    // Irreversible by design. The preferences wipe runs first and commits the decoy flag in
    // the same write, so a crash mid-wipe still leaves the node in decoy mode.
    fun executeNuclearWipe() {
        securePrefsManager.wipeForDuress()
        _isCorrupted.value = true

        try {
            WorkManager.getInstance(context).cancelAllWork()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel scheduled work during wipe", e)
        }
        try {
            context.deleteDatabase(ImpartDatabase.NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete the database during wipe", e)
        }
        keystoreHelper.purgeKeys()
    }

    private fun isWellFormed(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }

    companion object {
        private const val TAG = "DuressManager"
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
        private val PUBLIC_DEFAULTS = setOf(SecurePrefsManager.DEFAULT_STANDARD_PIN, SecurePrefsManager.DEFAULT_DURESS_PIN)
    }
}
