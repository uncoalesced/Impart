// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.handshake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.security.KeyGeneration
import com.uncoalesced.impart.core.security.KeyFingerprint
import com.uncoalesced.impart.core.security.QrPayloadCodec
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.HandshakePayload
import com.uncoalesced.impart.domain.rules.PhoneNumbers
import com.uncoalesced.impart.domain.usecase.AddContactResult
import com.uncoalesced.impart.domain.usecase.AddContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface QrState {
    data object Generating : QrState
    data class Ready(val payloadJson: String, val fingerprint: String) : QrState
    data class Unavailable(val message: String) : QrState
}

sealed interface ScanResult {
    data class Added(val contact: Contact) : ScanResult
    data class Refreshed(val contact: Contact) : ScanResult
    data class ConfirmKeyChange(
        val existing: Contact,
        val candidate: Contact,
        val oldFingerprint: String,
        val newFingerprint: String
    ) : ScanResult
    data class Rejected(val reason: String) : ScanResult
}

@HiltViewModel
class HandshakeViewModel @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val addContactUseCase: AddContactUseCase,
    private val securePrefsManager: SecurePrefsManager,
    private val keyGeneration: KeyGeneration
) : ViewModel() {

    // What the user is typing. Only a valid international number is ever persisted or put
    // into a QR, so a half-typed number never reaches a counterpart's Contact Matrix.
    private val phoneInput = MutableStateFlow(securePrefsManager.localPhoneNumber.value.orEmpty())
    val localPhoneNumber: StateFlow<String> = phoneInput.asStateFlow()

    val qrState: StateFlow<QrState> = combine(phoneInput, keyGeneration.value) { phone, _ -> phone }
        .map { buildQr(PhoneNumbers.normalize(it)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, QrState.Generating)

    private val _scanEvents = MutableSharedFlow<ScanResult>(extraBufferCapacity = 4)
    val scanEvents: SharedFlow<ScanResult> = _scanEvents.asSharedFlow()

    fun setLocalPhoneNumber(input: String) {
        phoneInput.value = input
        val normalized = PhoneNumbers.normalize(input)
        if (PhoneNumbers.isValidE164(normalized)) securePrefsManager.setLocalPhoneNumber(normalized)
    }

    fun onQrScanned(rawValue: String) {
        viewModelScope.launch {
            val event = QrPayloadCodec.decode(rawValue).mapCatching { payload ->
                if (!cryptoManager.isValidPublicKey(payload.publicKeyBase64) ||
                    !cryptoManager.isValidSigningKey(payload.signingKeyBase64)
                ) {
                    throw IllegalArgumentException("This QR does not carry valid Impart keys.")
                }
                addContactUseCase(payload, securePrefsManager.localUuid()).getOrThrow()
            }.fold(
                onSuccess = { result ->
                    when (result) {
                        is AddContactResult.Added -> ScanResult.Added(result.contact)
                        is AddContactResult.Refreshed -> ScanResult.Refreshed(result.contact)
                        is AddContactResult.KeyChanged -> ScanResult.ConfirmKeyChange(
                            existing = result.existing,
                            candidate = result.candidate,
                            oldFingerprint = fingerprint(result.existing),
                            newFingerprint = fingerprint(result.candidate)
                        )
                    }
                },
                onFailure = { ScanResult.Rejected(it.message ?: "Unrecognized QR code.") }
            )
            _scanEvents.emit(event)
        }
    }

    fun confirmKeyChange(existing: Contact, candidate: Contact) {
        viewModelScope.launch {
            val event = runCatching { addContactUseCase.confirmKeyChange(existing, candidate) }.fold(
                onSuccess = { ScanResult.Refreshed(it) },
                onFailure = { ScanResult.Rejected(it.message ?: "Could not update the contact.") }
            )
            _scanEvents.emit(event)
        }
    }

    private fun buildQr(phone: String): QrState {
        if (phone.isEmpty()) {
            return QrState.Unavailable("Enter your own number, with country code, to show your QR.")
        }
        if (!PhoneNumbers.isValidE164(phone)) {
            return QrState.Unavailable("That is not a full international number. Start with + and your country code.")
        }
        return runCatching {
            val publicKey = cryptoManager.getPublicKey()
            val signingKey = cryptoManager.getSigningPublicKey()
            QrState.Ready(
                payloadJson = QrPayloadCodec.encode(
                    HandshakePayload(
                        uuid = securePrefsManager.localUuid(),
                        phoneNumber = phone,
                        publicKeyBase64 = publicKey,
                        signingKeyBase64 = signingKey
                    )
                ),
                fingerprint = KeyFingerprint.of(publicKey, signingKey)
            )
        }.getOrElse { QrState.Unavailable("KEYSTORE UNAVAILABLE: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun fingerprint(contact: Contact): String =
        KeyFingerprint.of(contact.publicKeyBase64, contact.signingKeyBase64)
}
