// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.handshake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.security.QrPayloadCodec
import com.uncoalesced.impart.core.util.NodeIdentityHelper
import com.uncoalesced.impart.domain.model.HandshakePayload
import com.uncoalesced.impart.domain.usecase.AddContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HandshakeViewModel @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val addContactUseCase: AddContactUseCase,
    private val nodeIdentityHelper: NodeIdentityHelper
) : ViewModel() {

    private val _localPayloadJson = MutableStateFlow<String?>(null)
    val localPayloadJson: StateFlow<String?> = _localPayloadJson

    private val _payloadError = MutableStateFlow<String?>(null)
    val payloadError: StateFlow<String?> = _payloadError

    private val _localPhoneNumber = MutableStateFlow(nodeIdentityHelper.localPhoneNumber().orEmpty())
    val localPhoneNumber: StateFlow<String> = _localPhoneNumber

    fun setLocalPhoneNumber(number: String) {
        nodeIdentityHelper.setLocalPhoneNumber(number)
        _localPhoneNumber.value = number
        generatePayload()
    }

    fun generatePayload() {
        viewModelScope.launch {
            nodeIdentityHelper.resolve()
                .onSuccess { identity ->
                    val payload = HandshakePayload(
                        uuid = identity.uuid,
                        phoneNumber = identity.phoneNumber,
                        publicKeyBase64 = cryptoManager.getPublicKey()
                    )
                    _payloadError.value = null
                    _localPayloadJson.value = QrPayloadCodec.encode(payload)
                }
                .onFailure { error ->
                    _localPayloadJson.value = null
                    _payloadError.value =
                        "NODE IDENTITY UNAVAILABLE: ${error.message ?: "unknown error"}"
                }
        }
    }

    fun onQrScanned(jsonString: String) {
        val payload = QrPayloadCodec.decode(jsonString)
        if (payload != null) {
            viewModelScope.launch {
                addContactUseCase(payload)
            }
        }
    }
}
