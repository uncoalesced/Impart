// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.impart.core.security.AppLockManager
import com.uncoalesced.impart.core.security.CloakManager
import com.uncoalesced.impart.core.security.DeadManSwitchManager
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.security.UnlockResult
import com.uncoalesced.impart.core.util.LocationHelper
import com.uncoalesced.impart.core.util.NodeReadinessChecker
import com.uncoalesced.impart.core.util.ReadinessItem
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.displayName
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.domain.repository.RelayManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DashboardUiState {
    data object Idle : DashboardUiState
    data object Loading : DashboardUiState
    data class Success(val message: String) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

sealed interface NodeIdentityState {
    data class Ready(val uuid: String, val phoneNumber: String) : NodeIdentityState
    data class Unavailable(val uuid: String, val message: String) : NodeIdentityState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    contactRepository: ContactRepository,
    private val relayManager: RelayManager,
    private val locationHelper: LocationHelper,
    private val duressManager: DuressManager,
    private val appLockManager: AppLockManager,
    private val cloakManager: CloakManager,
    private val deadManSwitchManager: DeadManSwitchManager,
    private val securePrefsManager: SecurePrefsManager,
    private val readinessChecker: NodeReadinessChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Idle)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Manager-owned state is exposed directly; nothing is mirrored into a second flow.
    val isCloaked: StateFlow<Boolean> = cloakManager.isCloaked
    val dmsIntervalHours: StateFlow<Int> = deadManSwitchManager.intervalHours
    val dmsLastCheckin: StateFlow<Long> = deadManSwitchManager.lastCheckin
    val locationSharingEnabled: StateFlow<Boolean> = securePrefsManager.locationSharingEnabled
    val shufflePinPad: StateFlow<Boolean> = securePrefsManager.shufflePinPad

    val activeContacts: StateFlow<List<Contact>> = contactRepository.getActiveContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nodeIdentity: StateFlow<NodeIdentityState> = securePrefsManager.localPhoneNumber
        .map { toIdentityState(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, toIdentityState(securePrefsManager.localPhoneNumber.value))

    private val readinessTick = MutableStateFlow(0)

    // Re-evaluated when the user returns from Settings, pairs a contact, or sets a number.
    val readiness: StateFlow<List<ReadinessItem>> = combine(
        readinessTick, activeContacts, securePrefsManager.localPhoneNumber, securePrefsManager.vendorSettingsConfirmed
    ) { _, contacts, phone, vendorConfirmed ->
        readinessChecker.evaluate(
            phoneNumberSet = !phone.isNullOrBlank(),
            contactCount = contacts.size,
            vendorSettingsConfirmed = vendorConfirmed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // The vendor screens cannot be read back; the user's word is the only signal.
    fun confirmVendorSettings(confirmed: Boolean) = securePrefsManager.setVendorSettingsConfirmed(confirmed)

    fun refreshReadiness() {
        readinessTick.value += 1
    }

    fun initiatePanic(contact: Contact) {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            val location = resolveLocation()
            _uiState.value = relayManager.dispatchPanic(contact, location?.first, location?.second).fold(
                onSuccess = { DashboardUiState.Success("Panic dispatched to ${contact.displayName}") },
                onFailure = { DashboardUiState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun broadcastPanic(contacts: List<Contact>) {
        if (contacts.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            val location = resolveLocation()
            val report = relayManager.broadcastPanic(contacts, location?.first, location?.second)
            _uiState.value = if (report.allSent) {
                DashboardUiState.Success("Panic dispatched to ${report.sent} nodes")
            } else {
                DashboardUiState.Error(
                    "Sent to ${report.sent} of ${report.attempted} nodes. Failed: ${report.failed.joinToString()}"
                )
            }
        }
    }

    // Location is opt-in. Off means the payload carries no coordinates at all.
    private suspend fun resolveLocation(): Pair<Double, Double>? {
        if (!locationSharingEnabled.value) return null
        return locationHelper.getCurrentLocation()
    }

    fun setLocationSharingEnabled(enabled: Boolean) = securePrefsManager.setLocationSharingEnabled(enabled)

    // Same counters and lockout as the lock screen, and the duress PIN wipes here too.
    fun verifySecurityPin(pin: CharArray): UnlockResult = appLockManager.submitPin(pin)

    fun updatePins(standardPin: CharArray, duressPin: CharArray): Result<Unit> =
        duressManager.updatePins(standardPin, duressPin)

    fun toggleCloakMode(enabled: Boolean) = cloakManager.setCloakEnabled(enabled)

    fun setDmsInterval(hours: Int) = deadManSwitchManager.setIntervalHours(hours)

    fun dmsCheckIn() = deadManSwitchManager.checkIn()

    fun resetState() {
        _uiState.value = DashboardUiState.Idle
    }

    private fun toIdentityState(phone: String?): NodeIdentityState {
        val uuid = securePrefsManager.localUuid()
        return if (phone.isNullOrBlank()) {
            NodeIdentityState.Unavailable(uuid, "Enter your phone number on the My QR tab.")
        } else {
            NodeIdentityState.Ready(uuid, phone)
        }
    }
}
