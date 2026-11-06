// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.impart.core.sms.OrdinarySmsSender
import com.uncoalesced.impart.domain.model.SmsConversation
import com.uncoalesced.impart.domain.model.SmsMessageItem
import com.uncoalesced.impart.domain.repository.SmsStore
import com.uncoalesced.impart.domain.rules.PhoneNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Ordinary texts while Impart is the default SMS app (T14). Nothing here touches alerts.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val store: SmsStore,
    private val sender: OrdinarySmsSender
) : ViewModel() {

    val conversations: StateFlow<List<SmsConversation>> = store.conversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _openAddress = MutableStateFlow<String?>(null)
    val openAddress: StateFlow<String?> = _openAddress.asStateFlow()

    val thread: StateFlow<List<SmsMessageItem>> = _openAddress
        .flatMapLatest { address -> if (address == null) flowOf(emptyList()) else store.thread(address) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun open(address: String?) {
        _openAddress.value = address?.trim()?.takeIf { it.isNotBlank() }
        if (address != null) viewModelScope.launch { runCatching { store.markRead(address) } }
    }

    fun send(address: String, text: String) {
        val target = address.trim()
        if (target.isBlank() || text.isBlank()) return
        if (!PhoneNumbers.isValidE164(PhoneNumbers.normalize(target)) && target.any { !it.isDigit() && it != '+' && it != ' ' && it != '-' }) {
            viewModelScope.launch { _messages.emit("That does not look like a phone number.") }
            return
        }
        viewModelScope.launch {
            sender.send(target, text).fold(
                onSuccess = { _openAddress.value = target },
                onFailure = { _messages.emit("Could not send: ${it.message ?: it.javaClass.simpleName}") }
            )
        }
    }
}
