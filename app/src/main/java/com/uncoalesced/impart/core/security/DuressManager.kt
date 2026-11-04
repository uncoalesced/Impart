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

// The single place that decides what a PIN means. PINs are never stored or compared as
// strings: PinVerifier checks them against salted keyed hashes whose key lives in the
// Keystore. The published defaults 1234 and 9999 are only a refusal list at setup.
@Singleton
class DuressManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePrefsManager: SecurePrefsManager,
    private val keystoreHelper: KeystoreHelper,
    private val pinVerifier: PinVerifier,
    private val pinHasher: PinHasher
) : PinAuthority {
    private val _isCorrupted = MutableStateFlow(securePrefsManager.isNodeCorrupted())

    // Observed by MainActivity: the moment this flips the whole UI becomes the decoy.
    val isCorrupted: StateFlow<Boolean> = _isCorrupted.asStateFlow()

    override fun verifyPin(enteredPin: CharArray): PinVerificationResult {
        if (_isCorrupted.value) return PinVerificationResult.DURESS_TRIGGERED
        // Until the user has chosen PINs nothing unlocks and nothing wipes.
        if (!securePrefsManager.hasCustomPins.value) return PinVerificationResult.INVALID_PIN
        return when (pinVerifier.classify(enteredPin)) {
            PinMatch.STANDARD -> PinVerificationResult.STANDARD_SUCCESS
            PinMatch.DURESS -> {
                executeNuclearWipe()
                PinVerificationResult.DURESS_TRIGGERED
            }
            PinMatch.NONE -> PinVerificationResult.INVALID_PIN
        }
    }

    fun updatePins(standardPin: CharArray, duressPin: CharArray): Result<Unit> {
        if (!isWellFormed(standardPin) || !isWellFormed(duressPin)) {
            return Result.failure(
                IllegalArgumentException("PINs must be $MIN_PIN_LENGTH to $MAX_PIN_LENGTH digits.")
            )
        }
        if (standardPin.contentEquals(duressPin)) {
            return Result.failure(IllegalArgumentException("The duress PIN must differ from the standard PIN."))
        }
        if (isPublishedDefault(standardPin) || isPublishedDefault(duressPin)) {
            return Result.failure(IllegalArgumentException("1234 and 9999 are the published defaults. Choose other PINs."))
        }
        return pinVerifier.enroll(standardPin, duressPin)
    }

    // Irreversible by design. The preferences wipe runs first and commits the decoy flag in
    // the same write, so a crash mid-wipe still leaves the node in decoy mode.
    override fun executeNuclearWipe() {
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
        pinHasher.deleteKey()
    }

    private fun isWellFormed(pin: CharArray): Boolean =
        pin.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    private fun isPublishedDefault(pin: CharArray): Boolean =
        PUBLISHED_DEFAULTS.any { it.contentEquals(pin) }

    companion object {
        private const val TAG = "DuressManager"
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
        // The defaults every reader of the source knows. They unlock nothing and are refused at setup.
        val PUBLISHED_DEFAULTS: List<CharArray> = listOf("1234".toCharArray(), "9999".toCharArray())
    }
}
