// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.impart.core.security.AppLockManager
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.security.KeyFingerprint
import com.uncoalesced.impart.core.security.KeyGeneration
import com.uncoalesced.impart.core.security.UnlockResult
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.usecase.RotateKeysUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val appLockManager: AppLockManager,
    private val duressManager: DuressManager,
    private val securePrefsManager: SecurePrefsManager,
    private val rotateKeysUseCase: RotateKeysUseCase,
    private val keyGeneration: KeyGeneration
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Requires the standard PIN at the UI (RotateKeysFlow). Every contact then needs to scan
    // the new QR, and their alerts to this node fail until they do.
    fun rotateKeys() {
        viewModelScope.launch {
            rotateKeysUseCase().fold(
                onSuccess = { flagged ->
                    _messages.emit(
                        if (flagged == 0) "Keys rotated. No contacts to re-pair."
                        else "Keys rotated. $flagged contact(s) must scan your new QR, and theirs must be scanned again too."
                    )
                },
                onFailure = { _messages.emit("Key rotation failed: ${it.message ?: it.javaClass.simpleName}. Nothing was changed.") }
            )
        }
    }

    // Joel, 2026-11-04: optional hard cap, off by default. Changing it needs the standard PIN.
    val wipeAfterFailedAttempts: StateFlow<Boolean> = securePrefsManager.wipeAfterFailedAttemptsFlow

    fun setWipeAfterFailedAttempts(enabled: Boolean) = securePrefsManager.setWipeAfterFailedAttempts(enabled)

    private val _ownFingerprint = MutableStateFlow<String?>(null)
    val ownFingerprint: StateFlow<String?> = _ownFingerprint.asStateFlow()

    init {
        viewModelScope.launch {
            keyGeneration.value.collect {
                _ownFingerprint.value = withContext(Dispatchers.Default) {
                    runCatching { KeyFingerprint.of(cryptoManager.getPublicKey(), cryptoManager.getSigningPublicKey()) }
                        .getOrElse { "Keystore unavailable" }
                }
            }
        }
    }

    val shufflePinPad: StateFlow<Boolean> = securePrefsManager.shufflePinPad

    fun setShufflePinPad(enabled: Boolean) = securePrefsManager.setShufflePinPad(enabled)

    fun verifyPin(pin: CharArray): UnlockResult = appLockManager.submitPin(pin)

    fun updatePins(standardPin: CharArray, duressPin: CharArray): Result<Unit> =
        duressManager.updatePins(standardPin, duressPin)
}
