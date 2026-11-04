// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// The whole duress path on the JVM: the PIN decision, the order of the wipe, and what the
// node looks like afterwards.
class DuressManagerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeNodeState : NodeStateStore {
        val pins = MutableStateFlow(true)
        var wiped = false
        var legacyCorrupted = false
        override val hasCustomPins: StateFlow<Boolean> get() = pins
        override fun wipeForDuress() { wiped = true; pins.value = false }
        override fun isNodeCorrupted(): Boolean = legacyCorrupted
    }

    private class FakeDecoyFlag(private val log: MutableList<String>) : DecoyFlagStore {
        var set = false
        override fun isSet(): Boolean = set
        override fun set() { set = true; log += "decoyFlag" }
    }

    private class ForgettingKeys(private val log: MutableList<String>) : KeysetSource {
        override fun keysetHandle() = throw UnsupportedOperationException()
        override fun signingKeysetHandle() = throw UnsupportedOperationException()
        override fun forgetKeys() { log += "forgetKeys" }
        override fun rotateKeys() = throw UnsupportedOperationException()
    }

    private val log = mutableListOf<String>()
    private val nodeState = FakeNodeState()
    private val decoyFlag = FakeDecoyFlag(log)
    private val store = MemoryPinStore()
    private val hasher = SoftwarePinHasher()
    private val verifier = PinVerifier(store, hasher)

    private fun manager(world: FakeWipeWorld): DuressManager {
        val recordingSideEffects = object : WipeSideEffects by world {
            override fun cancelAllWork() { log += "cancelAllWork"; world.cancelAllWork() }
        }
        return DuressManager(nodeState, ForgettingKeys(log), verifier, decoyFlag, world, recordingSideEffects).also {
            it.updatePins("2580".toCharArray(), "7531".toCharArray()).getOrThrow()
        }
    }

    @Test
    fun `standard pin unlocks and duress pin wipes`() {
        val world = FakeWipeWorld(temp.root)
        world.populate()
        val manager = manager(world)

        assertEquals(PinVerificationResult.STANDARD_SUCCESS, manager.verifyPin("2580".toCharArray()))
        assertEquals(PinVerificationResult.INVALID_PIN, manager.verifyPin("0000".toCharArray()))
        assertFalse(manager.isCorrupted.value)

        assertEquals(PinVerificationResult.DURESS_TRIGGERED, manager.verifyPin("7531".toCharArray()))
        assertTrue(manager.isCorrupted.value)
        assertTrue(decoyFlag.set)
        assertTrue(nodeState.wiped)
        assertEquals(listOf(world.decoyFlagFile.canonicalPath), world.everythingUnderDataDir().map { it.canonicalPath })
        assertEquals(setOf("unrelated_alias"), world.aliases)
        assertTrue(world.events.contains("cancelAllNotifications"))
    }

    @Test
    fun `the decoy flag is committed before anything is deleted`() {
        val world = FakeWipeWorld(temp.root)
        world.populate()
        manager(world).executeNuclearWipe()
        assertEquals(listOf("decoyFlag", "forgetKeys", "cancelAllWork"), log.take(3))
    }

    @Test
    fun `after the wipe every pin is duress and nothing unlocks`() {
        val world = FakeWipeWorld(temp.root)
        val manager = manager(world)
        manager.executeNuclearWipe()
        assertEquals(PinVerificationResult.DURESS_TRIGGERED, manager.verifyPin("2580".toCharArray()))
        assertEquals(PinVerificationResult.DURESS_TRIGGERED, manager.verifyPin("0000".toCharArray()))
    }

    @Test
    fun `a fresh process reads the plain decoy flag`() {
        val world = FakeWipeWorld(temp.root)
        decoyFlag.set = true
        val fresh = DuressManager(nodeState, ForgettingKeys(log), verifier, decoyFlag, world, world)
        assertTrue(fresh.isCorrupted.value)
    }

    @Test
    fun `an older install's flag inside the encrypted preferences is honoured`() {
        val world = FakeWipeWorld(temp.root)
        nodeState.legacyCorrupted = true
        val fresh = DuressManager(nodeState, ForgettingKeys(log), verifier, decoyFlag, world, world)
        assertTrue(fresh.isCorrupted.value)
    }

    @Test
    fun `without custom pins nothing unlocks and nothing wipes`() {
        val world = FakeWipeWorld(temp.root)
        world.populate()
        val manager = manager(world)
        nodeState.pins.value = false
        assertEquals(PinVerificationResult.INVALID_PIN, manager.verifyPin("2580".toCharArray()))
        assertEquals(PinVerificationResult.INVALID_PIN, manager.verifyPin("7531".toCharArray()))
        assertFalse(manager.isCorrupted.value)
        assertTrue(world.everythingUnderDataDir().size > 20)
    }

    @Test
    fun `published defaults and malformed pins are refused at setup`() {
        val manager = manager(FakeWipeWorld(temp.root))
        assertTrue(manager.updatePins("1234".toCharArray(), "7531".toCharArray()).isFailure)
        assertTrue(manager.updatePins("2580".toCharArray(), "9999".toCharArray()).isFailure)
        assertTrue(manager.updatePins("2580".toCharArray(), "2580".toCharArray()).isFailure)
        assertTrue(manager.updatePins("258".toCharArray(), "7531".toCharArray()).isFailure)
        assertTrue(manager.updatePins("258012345".toCharArray(), "7531".toCharArray()).isFailure)
        assertTrue(manager.updatePins("25a0".toCharArray(), "7531".toCharArray()).isFailure)
        assertTrue(manager.updatePins("2580".toCharArray(), "75310".toCharArray()).isSuccess)
    }
}
