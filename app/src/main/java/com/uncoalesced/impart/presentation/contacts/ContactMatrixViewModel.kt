// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.domain.usecase.RenameContactUseCase
import com.uncoalesced.impart.domain.usecase.RevokeContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactMatrixViewModel @Inject constructor(
    contactRepository: ContactRepository,
    private val revokeContactUseCase: RevokeContactUseCase,
    private val renameContactUseCase: RenameContactUseCase
) : ViewModel() {

    val contacts: StateFlow<List<Contact>> = contactRepository.getActiveContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun revokeContact(uuid: String) {
        viewModelScope.launch { revokeContactUseCase(uuid) }
    }

    fun renameContact(uuid: String, nickname: String) {
        viewModelScope.launch {
            renameContactUseCase(uuid, nickname).onFailure { error ->
                _messages.emit(error.message ?: "Could not rename contact.")
            }
        }
    }
}
