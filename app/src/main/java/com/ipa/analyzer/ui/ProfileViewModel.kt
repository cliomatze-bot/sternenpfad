package com.ipa.analyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ipa.analyzer.AnalyzerApp
import com.ipa.analyzer.core.api.LookupResult
import com.ipa.analyzer.data.ApiCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val query: String = "",
    val loading: Boolean = false,
    val result: LookupResult? = null,
    /** Letzter erfolgreicher Abruf – Grundlage für die Beitragsübersicht. */
    val lastSuccess: LookupResult.Success? = null,
)

/** Profilsuche über die offizielle Instagram Graph API sowie API-Einstellungen. */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AnalyzerApp

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _credentials = MutableStateFlow(app.settings.credentials())
    val credentials: StateFlow<ApiCredentials> = _credentials.asStateFlow()

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun lookup() {
        val q = _state.value.query
        if (q.isBlank() || _state.value.loading) return
        _state.value = _state.value.copy(loading = true, result = null)
        viewModelScope.launch {
            val creds = app.settings.credentials()
            val r = withContext(Dispatchers.IO) { app.api.lookup(q, creds) }
            _state.value = _state.value.copy(
                loading = false,
                result = r,
                lastSuccess = (r as? LookupResult.Success) ?: _state.value.lastSuccess,
            )
        }
    }

    fun saveCredentials(c: ApiCredentials) {
        app.settings.saveCredentials(c)
        _credentials.value = app.settings.credentials()
    }

    /** Nach „Alle Daten löschen“ aufrufen. */
    fun resetAfterWipe() {
        _credentials.value = app.settings.credentials()
        _state.value = ProfileUiState()
    }
}
