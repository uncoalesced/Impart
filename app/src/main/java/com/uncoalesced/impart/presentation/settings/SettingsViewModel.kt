// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.impart.core.security.AppLockManager
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.security.KeyFingerprint
import com.uncoalesced.impart.core.security.UnlockResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val appLockManager: AppLockManager,
    private val duressManager: DuressManager
) : ViewModel() {

    private val _ownFingerprint = MutableStateFlow<String?>(null)
    val ownFingerprint: StateFlow<String?> = _ownFingerprint.asStateFlow()

    init {
        viewModelScope.launch {
            _ownFingerprint.value = withContext(Dispatchers.Default) {
                runCatching { KeyFingerprint.of(cryptoManager.getPublicKey(), cryptoManager.getSigningPublicKey()) }
                    .getOrElse { "Keystore unavailable" }
            }
        }
    }

    fun verifyPin(pin: String): UnlockResult = appLockManager.submitPin(pin)

    fun updatePins(standardPin: String, duressPin: String): Result<Unit> = duressManager.updatePins(standardPin, duressPin)
}
