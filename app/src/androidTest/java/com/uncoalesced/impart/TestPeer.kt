// Engineered by uncoalesced
package com.uncoalesced.impart

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.signature.SignatureConfig
import com.uncoalesced.impart.core.security.CryptoManager
import com.uncoalesced.impart.core.security.KeysetSource
import com.uncoalesced.impart.core.security.KeystoreHelper
import com.uncoalesced.impart.core.security.QrPayloadCodec
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.HandshakePayload
import java.util.UUID

// Another phone, in memory: real Tink keysets, no Keystore. Its QR is what a camera would see.
class TestPeer(val uuid: String = UUID.randomUUID().toString(), val phone: String = "+15550001111") {
    private val keys = object : KeysetSource {
        private val hybrid = KeysetHandle.generateNew(KeyTemplates.get(KeystoreHelper.HYBRID_KEY_TEMPLATE))
        private val signing = KeysetHandle.generateNew(KeyTemplates.get(KeystoreHelper.SIGNING_KEY_TEMPLATE))
        override fun keysetHandle(): KeysetHandle = hybrid
        override fun signingKeysetHandle(): KeysetHandle = signing
        override fun rotateKeys() = throw UnsupportedOperationException()
    }

    val crypto = CryptoManager(keys)

    fun qr(): String = QrPayloadCodec.encode(HandshakePayload(uuid, phone, crypto.getPublicKey(), crypto.getSigningPublicKey()))

    fun asContact(nickname: String? = null) = Contact(
        uuid = uuid,
        publicKeyBase64 = crypto.getPublicKey(),
        signingKeyBase64 = crypto.getSigningPublicKey(),
        nickname = nickname,
        phoneNumber = phone
    )

    companion object {
        init {
            HybridConfig.register()
            SignatureConfig.register()
        }
    }
}
