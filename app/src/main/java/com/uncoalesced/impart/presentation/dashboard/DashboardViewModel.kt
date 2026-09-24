// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.impart.core.security.CloakManager
import com.uncoalesced.impart.core.security.DeadManSwitchManager
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.security.PinVerificationResult
import com.uncoalesced.impart.core.util.LocationHelper
import com.uncoalesced.impart.core.util.NodeIdentityHelper
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.domain.repository.RelayManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DashboardUiState {
    object Idle : DashboardUiState
    object Loading : DashboardUiState
    object Success : DashboardUiState
    object NodeCorrupted : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

sealed interface NodeIdentityState {
    object Loading : NodeIdentityState
    data class Ready(val uuid: String, val phoneNumber: String) : NodeIdentityState
    data class Unavailable(val uuid: String, val message: String) : NodeIdentityState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    contactRepository: ContactRepository,
    private val relayManager: RelayManager,
    private val locationHelper: LocationHelper,
    private val duressManager: DuressManager,
    private val cloakManager: CloakManager,
    private val deadManSwitchManager: DeadManSwitchManager,
    private val securePrefsManager: SecurePrefsManager,
    private val nodeIdentityHelper: NodeIdentityHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Idle)
    val uiState: StateFlow<DashboardUiState> = _uiState

    private val _isCloaked = MutableStateFlow(cloakManager.isCloaked())
    val isCloaked: StateFlow<Boolean> = _isCloaked

    private val _dmsIntervalHours = MutableStateFlow(deadManSwitchManager.getIntervalHours())
    val dmsIntervalHours: StateFlow<Int> = _dmsIntervalHours

    private val _dmsLastCheckin = MutableStateFlow(deadManSwitchManager.getLastCheckin())
    val dmsLastCheckin: StateFlow<Long> = _dmsLastCheckin

    private val _locationSharingEnabled = MutableStateFlow(securePrefsManager.isLocationSharingEnabled())
    val locationSharingEnabled: StateFlow<Boolean> = _locationSharingEnabled

    private val _nodeIdentity = MutableStateFlow<NodeIdentityState>(NodeIdentityState.Loading)
    val nodeIdentity: StateFlow<NodeIdentityState> = _nodeIdentity

    val activeContacts: StateFlow<List<Contact>> = contactRepository.getActiveContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val uuid = nodeIdentityHelper.localUuid()
            _nodeIdentity.value = nodeIdentityHelper.resolve().fold(
                onSuccess = { NodeIdentityState.Ready(it.uuid, it.phoneNumber) },
                onFailure = {
                    NodeIdentityState.Unavailable(uuid, it.message ?: "Phone number unavailable")
                }
            )
        }
    }

    fun initiatePanic(contact: Contact) {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading

            val loc = resolveLocation()

            val result = relayManager.dispatchPanic(
                targetPhoneNumber = contact.phoneNumber,
                counterpartPublicKeyBase64 = contact.publicKeyBase64,
                lat = loc?.first,
                lng = loc?.second
            )
            
            if (result.isSuccess) {
                _uiState.value = DashboardUiState.Success
            } else {
                _uiState.value = DashboardUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun broadcastPanic(contacts: List<Contact>) {
        if (contacts.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading

            val loc = resolveLocation()

            val deferreds = contacts.map { contact ->
                async {
                    relayManager.dispatchPanic(
                        targetPhoneNumber = contact.phoneNumber,
                        counterpartPublicKeyBase64 = contact.publicKeyBase64,
                        lat = loc?.first,
                        lng = loc?.second
                    )
                }
            }
            
            val results = deferreds.awaitAll()
            if (results.any { it.isFailure }) {
                _uiState.value = DashboardUiState.Error("One or more dispatches failed.")
            } else {
                _uiState.value = DashboardUiState.Success
            }
        }
    }

    // Location is opt-in. Off means the panic payload carries no coordinates at all,
    // not a stale or coarse fallback.
    private suspend fun resolveLocation(): Pair<Double, Double>? {
        if (!_locationSharingEnabled.value) return null
        return locationHelper.getCurrentLocation()
    }

    fun setLocationSharingEnabled(enabled: Boolean) {
        securePrefsManager.setLocationSharingEnabled(enabled)
        _locationSharingEnabled.value = enabled
    }

    fun verifySecurityPin(pin: String): PinVerificationResult {
        val result = duressManager.verifyPin(pin)
        if (result == PinVerificationResult.DURESS_TRIGGERED) {
            _uiState.value = DashboardUiState.NodeCorrupted
        }
        return result
    }

    fun toggleCloakMode(enabled: Boolean) {
        cloakManager.setCloakEnabled(enabled)
        _isCloaked.value = enabled
    }

    fun setDmsInterval(hours: Int) {
        deadManSwitchManager.setIntervalHours(hours)
        _dmsIntervalHours.value = hours
        _dmsLastCheckin.value = deadManSwitchManager.getLastCheckin()
    }

    fun dmsCheckIn() {
        deadManSwitchManager.checkIn()
        _dmsLastCheckin.value = deadManSwitchManager.getLastCheckin()
    }

    fun isNodeCorrupted(): Boolean = duressManager.isNodeCorrupted()

    fun resetState() {
        _uiState.value = DashboardUiState.Idle
    }
}
