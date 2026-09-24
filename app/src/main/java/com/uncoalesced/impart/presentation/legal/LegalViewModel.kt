// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.impart.data.local.assets.LegalDocumentLoader
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.LegalBlock
import com.uncoalesced.impart.domain.model.LegalDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LegalDocumentState {
    data object Closed : LegalDocumentState
    data class Loading(val document: LegalDocument) : LegalDocumentState
    data class Loaded(val document: LegalDocument, val blocks: List<LegalBlock>) : LegalDocumentState
    data class Failed(val document: LegalDocument, val message: String) : LegalDocumentState
}

@HiltViewModel
class LegalViewModel @Inject constructor(
    private val loader: LegalDocumentLoader,
    private val securePrefsManager: SecurePrefsManager
) : ViewModel() {

    private val _state = MutableStateFlow<LegalDocumentState>(LegalDocumentState.Closed)
    val state: StateFlow<LegalDocumentState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun open(document: LegalDocument) {
        loadJob?.cancel()
        _state.value = LegalDocumentState.Loading(document)
        loadJob = viewModelScope.launch {
            _state.value = loader.load(document).fold(
                onSuccess = { LegalDocumentState.Loaded(document, it) },
                onFailure = { LegalDocumentState.Failed(document, it.message ?: "Could not open ${document.title}.") }
            )
        }
    }

    fun close() {
        loadJob?.cancel()
        _state.value = LegalDocumentState.Closed
    }

    fun accept() = securePrefsManager.acceptLegal(LegalDocument.CURRENT_VERSION)
}
