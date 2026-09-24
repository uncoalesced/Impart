// Engineered by uncoalesced
package com.uncoalesced.impart.domain.usecase

import com.uncoalesced.impart.domain.model.HandshakePayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddContactUseCaseTest {

    private val repository = FakeContactRepository()
    private val useCase = AddContactUseCase(repository)
    private val localUuid = "local-uuid"
    private val payload = HandshakePayload(
        uuid = "peer-uuid",
        phoneNumber = " +1 (555) 123-4567 ",
        publicKeyBase64 = "pub",
        signingKeyBase64 = "sign"
    )

    @Test
    fun `new node is added with a normalized number`() = runBlocking {
        val result = useCase(payload, localUuid).getOrThrow()

        assertTrue(result is AddContactResult.Added)
        val contact = (result as AddContactResult.Added).contact
        assertEquals("+15551234567", contact.phoneNumber)
        assertEquals("sign", contact.signingKeyBase64)
        assertEquals(1, repository.contacts.size)
    }

    @Test
    fun `rescanning the same keys keeps the nickname and un-revokes`() = runBlocking {
        useCase(payload, localUuid)
        repository.renameContact("peer-uuid", "Alice")
        repository.revokeContact("peer-uuid")

        val result = useCase(payload.copy(phoneNumber = "+15559999999"), localUuid).getOrThrow()

        assertTrue(result is AddContactResult.Refreshed)
        val stored = repository.getContactByUuid("peer-uuid")!!
        assertEquals("Alice", stored.nickname)
        assertEquals("+15559999999", stored.phoneNumber)
        assertFalse(stored.revoked)
    }

    // The QR takeover from the security review: a known UUID with someone else's keys and
    // number. Nothing may change until the user confirms.
    @Test
    fun `different keys for a known uuid write nothing until confirmed`() = runBlocking {
        useCase(payload, localUuid)
        repository.renameContact("peer-uuid", "Alice")

        val hijack = payload.copy(phoneNumber = "+15550006666", publicKeyBase64 = "evil-pub", signingKeyBase64 = "evil-sign")
        val result = useCase(hijack, localUuid).getOrThrow()

        assertTrue(result is AddContactResult.KeyChanged)
        val untouched = repository.getContactByUuid("peer-uuid")!!
        assertEquals("pub", untouched.publicKeyBase64)
        assertEquals("+15551234567", untouched.phoneNumber)

        result as AddContactResult.KeyChanged
        val replaced = useCase.confirmKeyChange(result.existing, result.candidate)
        assertEquals("evil-pub", replaced.publicKeyBase64)
        assertEquals("Alice", replaced.nickname)
    }

    @Test
    fun `only the signing key changing is also a key change`() = runBlocking {
        useCase(payload, localUuid)
        assertTrue(useCase(payload.copy(signingKeyBase64 = "other"), localUuid).getOrThrow() is AddContactResult.KeyChanged)
    }

    @Test
    fun `short codes and local numbers are rejected`() = runBlocking {
        assertTrue(useCase(payload.copy(phoneNumber = "88088"), localUuid).isFailure)
        assertTrue(useCase(payload.copy(phoneNumber = "07700900123"), localUuid).isFailure)
        assertTrue(useCase(payload.copy(phoneNumber = "  "), localUuid).isFailure)
        assertTrue(repository.contacts.isEmpty())
    }

    @Test
    fun `scanning your own QR is rejected`() = runBlocking {
        assertTrue(useCase(payload.copy(uuid = localUuid), localUuid).isFailure)
        assertTrue(repository.contacts.isEmpty())
    }

    @Test
    fun `blank uuid or either key is rejected`() = runBlocking {
        assertTrue(useCase(payload.copy(uuid = ""), localUuid).isFailure)
        assertTrue(useCase(payload.copy(publicKeyBase64 = ""), localUuid).isFailure)
        assertTrue(useCase(payload.copy(signingKeyBase64 = ""), localUuid).isFailure)
        assertTrue(repository.contacts.isEmpty())
    }
}
