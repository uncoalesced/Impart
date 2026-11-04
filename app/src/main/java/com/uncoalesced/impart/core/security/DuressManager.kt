// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

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
    private val nodeState: NodeStateStore,
    private val keys: KeysetSource,
    private val pinVerifier: PinVerifier,
    private val decoyFlag: DecoyFlagStore,
    wipeTargets: WipeTargets,
    wipeSideEffects: WipeSideEffects
) : PinAuthority {
    private val wiper = DuressWiper(wipeTargets, wipeSideEffects)

    // The plain decoy flag is the source of truth; the encrypted preferences no longer
    // survive the wipe. Older installs that set the flag inside the encrypted preferences
    // are honoured too.
    private val _isCorrupted = MutableStateFlow(decoyFlag.isSet() || nodeState.isNodeCorrupted())

    // Observed by MainActivity: the moment this flips the whole UI becomes the decoy.
    val isCorrupted: StateFlow<Boolean> = _isCorrupted.asStateFlow()

    override fun verifyPin(enteredPin: CharArray): PinVerificationResult {
        if (_isCorrupted.value) return PinVerificationResult.DURESS_TRIGGERED
        // Until the user has chosen PINs nothing unlocks and nothing wipes.
        if (!nodeState.hasCustomPins.value) return PinVerificationResult.INVALID_PIN
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

    // Irreversible by design, and silent. The decoy flag is committed first, so a crash at
    // any later step still leaves the node in decoy mode. Then the encrypted preferences
    // are cleared, the in-memory keysets dropped, and DuressWiper removes every file,
    // database and Keystore alias in the inventory (spec 03 section 6.2). The launcher
    // cloak state is left exactly as it was (Joel, 2026-11-04).
    override fun executeNuclearWipe() {
        decoyFlag.set()
        _isCorrupted.value = true
        nodeState.wipeForDuress()
        keys.forgetKeys()
        wiper.wipe()
    }

    private fun isWellFormed(pin: CharArray): Boolean =
        pin.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    private fun isPublishedDefault(pin: CharArray): Boolean =
        PUBLISHED_DEFAULTS.any { it.contentEquals(pin) }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
        // The defaults every reader of the source knows. They unlock nothing and are refused at setup.
        val PUBLISHED_DEFAULTS: List<CharArray> = listOf("1234".toCharArray(), "9999".toCharArray())
    }
}
