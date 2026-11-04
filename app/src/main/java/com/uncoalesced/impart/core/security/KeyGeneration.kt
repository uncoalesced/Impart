// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Counts key rotations in this process so the QR and the fingerprint rebuild after one.
@Singleton
class KeyGeneration @Inject constructor() {
    private val _value = MutableStateFlow(0)
    val value: StateFlow<Int> = _value.asStateFlow()

    fun bump() {
        _value.value += 1
    }
}
