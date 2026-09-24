// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.signature.SignatureConfig
import com.uncoalesced.impart.domain.model.Contact
import java.util.UUID

// An in-memory node for JVM tests: real Tink keysets, no Android Keystore.
class TestNode(val uuid: String = UUID.randomUUID().toString(), val phone: String = "+15550000000") {
    private val keys = object : KeysetSource {
        private val hybrid = KeysetHandle.generateNew(KeyTemplates.get(KeystoreHelper.HYBRID_KEY_TEMPLATE))
        private val signing = KeysetHandle.generateNew(KeyTemplates.get(KeystoreHelper.SIGNING_KEY_TEMPLATE))
        override fun keysetHandle(): KeysetHandle = hybrid
        override fun signingKeysetHandle(): KeysetHandle = signing
    }

    val crypto = CryptoManager(keys)

    // How another node stores this one after scanning its QR.
    fun asContact(nickname: String? = null): Contact = Contact(
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
