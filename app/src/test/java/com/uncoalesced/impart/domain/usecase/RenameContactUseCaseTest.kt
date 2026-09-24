// Engineered by uncoalesced
package com.uncoalesced.impart.domain.usecase

import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.displayName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenameContactUseCaseTest {

    private val repository = FakeContactRepository()
    private val useCase = RenameContactUseCase(repository)

    private fun contact(uuid: String = "u") = Contact(uuid = uuid, publicKeyBase64 = "k", signingKeyBase64 = "s", phoneNumber = "+1")

    @Test
    fun `rename trims and persists`() = runBlocking {
        repository.addContact(contact())
        assertTrue(useCase("u", "  Alice  ").isSuccess)
        assertEquals("Alice", repository.getContactByUuid("u")?.nickname)
    }

    @Test
    fun `blank nickname is rejected`() = runBlocking {
        repository.addContact(contact())
        assertTrue(useCase("u", "   ").isFailure)
    }

    @Test
    fun `overlong nickname is rejected`() = runBlocking {
        repository.addContact(contact())
        assertTrue(useCase("u", "x".repeat(33)).isFailure)
    }

    @Test
    fun `display name prefers nickname then phone then uuid stub`() {
        val base = Contact(uuid = "3fa2c1b9-rest", publicKeyBase64 = "k", signingKeyBase64 = "s")
        assertEquals("3fa2c1b9", base.displayName)
        assertEquals("+15551234567", base.copy(phoneNumber = "+15551234567").displayName)
        assertEquals("Alice", base.copy(phoneNumber = "+1", nickname = "Alice").displayName)
        assertEquals("+1", base.copy(phoneNumber = "+1", nickname = "  ").displayName)
    }
}
