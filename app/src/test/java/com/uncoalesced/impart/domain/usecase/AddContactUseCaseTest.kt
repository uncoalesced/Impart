// Engineered by uncoalesced
package com.uncoalesced.impart.domain.usecase

import com.uncoalesced.impart.domain.model.HandshakePayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddContactUseCaseTest {

    private val repository = FakeContactRepository()
    private val useCase = AddContactUseCase(repository)
    private val localUuid = "local-uuid"
    private val payload = HandshakePayload(uuid = "peer-uuid", phoneNumber = " +15551234567 ", publicKeyBase64 = "key")

    @Test
    fun `stores a trimmed contact and returns it`() = runBlocking {
        val contact = useCase(payload, localUuid).getOrThrow()

        assertEquals("peer-uuid", contact.uuid)
        assertEquals("+15551234567", contact.phoneNumber)
        assertEquals("key", contact.publicKeyBase64)
        assertEquals(1, repository.contacts.size)
    }

    @Test
    fun `scanning your own QR is rejected and nothing is stored`() = runBlocking {
        val result = useCase(payload.copy(uuid = localUuid), localUuid)

        assertTrue(result.isFailure)
        assertTrue(repository.contacts.isEmpty())
    }

    @Test
    fun `blank phone number is rejected`() = runBlocking {
        assertTrue(useCase(payload.copy(phoneNumber = "  "), localUuid).isFailure)
        assertTrue(repository.contacts.isEmpty())
    }

    @Test
    fun `blank uuid or key is rejected`() = runBlocking {
        assertTrue(useCase(payload.copy(uuid = ""), localUuid).isFailure)
        assertTrue(useCase(payload.copy(publicKeyBase64 = ""), localUuid).isFailure)
        assertTrue(repository.contacts.isEmpty())
    }

    @Test
    fun `re-scanning the same node replaces rather than duplicates`() = runBlocking {
        useCase(payload, localUuid)
        useCase(payload.copy(phoneNumber = "+15559999999"), localUuid)

        assertEquals(1, repository.contacts.size)
        assertEquals("+15559999999", repository.contacts.single().phoneNumber)
    }
}
