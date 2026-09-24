// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.handshake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.security.QrPayloadCodec
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.HandshakePayload
import com.uncoalesced.impart.domain.usecase.AddContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface QrState {
    data object Generating : QrState
    data class Ready(val payloadJson: String) : QrState
    data class Unavailable(val message: String) : QrState
}

sealed interface ScanResult {
    data class Added(val contact: Contact) : ScanResult
    data class Rejected(val reason: String) : ScanResult
}

@HiltViewModel
class HandshakeViewModel @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val addContactUseCase: AddContactUseCase,
    private val securePrefsManager: SecurePrefsManager
) : ViewModel() {

    val localPhoneNumber: StateFlow<String> = securePrefsManager.localPhoneNumber
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, securePrefsManager.localPhoneNumber.value.orEmpty())

    // Re-derived whenever the phone number changes. Keystore access happens off the main
    // thread because first-run key generation is not instant.
    val qrState: StateFlow<QrState> = securePrefsManager.localPhoneNumber
        .map { buildQr() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, QrState.Generating)

    private val _scanEvents = MutableSharedFlow<ScanResult>(extraBufferCapacity = 4)
    val scanEvents: SharedFlow<ScanResult> = _scanEvents.asSharedFlow()

    fun setLocalPhoneNumber(number: String) {
        securePrefsManager.setLocalPhoneNumber(number)
    }

    fun onQrScanned(rawValue: String) {
        viewModelScope.launch {
            val result = QrPayloadCodec.decode(rawValue).mapCatching { payload ->
                if (!cryptoManager.isValidPublicKey(payload.publicKeyBase64)) {
                    throw IllegalArgumentException("This QR does not carry a valid Impart public key.")
                }
                addContactUseCase(payload, securePrefsManager.localUuid()).getOrThrow()
            }
            _scanEvents.emit(
                result.fold(
                    onSuccess = { ScanResult.Added(it) },
                    onFailure = { ScanResult.Rejected(it.message ?: "Unrecognized QR code.") }
                )
            )
        }
    }

    private fun buildQr(): QrState = securePrefsManager.nodeIdentity().fold(
        onSuccess = { identity ->
            runCatching {
                QrPayloadCodec.encode(
                    HandshakePayload(
                        uuid = identity.uuid,
                        phoneNumber = identity.phoneNumber,
                        publicKeyBase64 = cryptoManager.getPublicKey()
                    )
                )
            }.fold(
                onSuccess = { QrState.Ready(it) },
                onFailure = { QrState.Unavailable("KEYSTORE UNAVAILABLE: ${it.message ?: it.javaClass.simpleName}") }
            )
        },
        onFailure = { QrState.Unavailable("NODE IDENTITY UNAVAILABLE: ${it.message ?: "unknown error"}") }
    )
}
