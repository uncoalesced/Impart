// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import kotlinx.coroutines.flow.StateFlow

// What DuressManager needs from the encrypted preferences. SecurePrefsManager implements it;
// JVM tests use an in-memory fake.
interface NodeStateStore {
    val hasCustomPins: StateFlow<Boolean>
    fun wipeForDuress()
    // Legacy flag: builds before T08 set it inside the encrypted preferences.
    fun isNodeCorrupted(): Boolean
}
