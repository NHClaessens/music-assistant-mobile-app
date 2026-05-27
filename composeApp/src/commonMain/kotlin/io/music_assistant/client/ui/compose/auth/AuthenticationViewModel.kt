package io.music_assistant.client.ui.compose.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthenticationViewModel(
    private val authManager: AuthenticationManager,
    serviceClient: ServiceClient,
) : ViewModel() {
    private val _providers = MutableStateFlow<List<AuthProvider>>(emptyList())
    val providers: StateFlow<List<AuthProvider>> = _providers.asStateFlow()

    private var loadProvidersJob: Job? = null
    private var loadingForWebRTC: Boolean? = null

    val authState = authManager.authState
    val sessionState = serviceClient.sessionState

    val username = MutableStateFlow("")
    val password = MutableStateFlow("")

    init {
        // sessionState is a StateFlow → emits its current value to the new collector
        // immediately, so this single launch covers both initial-state handling and
        // subsequent transitions.
        viewModelScope.launch {
            sessionState.collect(::handleSessionState)
        }
    }

    private fun handleSessionState(state: SessionState) {
        log.d { "SessionState changed: ${state::class.simpleName}" }
        when (state) {
            is SessionState.Connected -> onConnected(state.dataConnectionState)
            is SessionState.Disconnected -> onDisconnected()
            else -> { /* Connecting, Reconnecting — no action */ }
        }
    }

    private fun onConnected(dataConnectionState: DataConnectionState) {
        log.d { "DataConnectionState: ${dataConnectionState::class.simpleName}" }
        if (dataConnectionState !is DataConnectionState.AwaitingAuth) return
        // Skip reload on Failed so we don't overwrite the displayed error.
        if (dataConnectionState.authProcessState is AuthProcessState.Failed) {
            log.d { "Auth failed - not reloading providers" }
            return
        }
        log.d { "AwaitingAuth - loading providers" }
        loadProviders()
    }

    private fun onDisconnected() {
        // Clear providers so the next connection (WebRTC ↔ Direct) refetches.
        log.d { "Disconnected - clearing providers and cancelling pending load" }
        loadProvidersJob?.cancel()
        loadProvidersJob = null
        loadingForWebRTC = null
        _providers.update { emptyList() }
    }

    fun loadProviders() {
        log.d { "loadProviders() called, current providers count: ${_providers.value.size}" }

        // Don't reload if we already have providers (to avoid overriding error states)
        if (_providers.value.isNotEmpty()) {
            return
        }

        val currentState = sessionState.value
        val isWebRTC = currentState is SessionState.Connected.WebRTC

        // If a job is running for the SAME connection type, skip (avoid redundant calls)
        if (loadProvidersJob?.isActive == true && loadingForWebRTC == isWebRTC) {
            log.d { "Provider loading already in progress for same connection type, skipping" }
            return
        }

        // Cancel if connection type changed (WebRTC ↔ Direct) - old result would be wrong
        if (loadProvidersJob?.isActive == true && loadingForWebRTC != isWebRTC) {
            log.d { "Connection type changed, cancelling previous load" }
            loadProvidersJob?.cancel()
            loadProvidersJob = null
        }

        loadingForWebRTC = isWebRTC

        if (isWebRTC) {
            // For WebRTC, skip API call - only builtin auth works (OAuth requires HTTP redirects)
            log.d { "WebRTC connection - using builtin provider directly (skip API call)" }
            val builtinProvider = AuthProvider(
                id = "builtin",
                type = "builtin",
                requiresRedirect = false,
            )
            _providers.update { listOf(builtinProvider) }
            // Clear job reference since we're done (synchronous)
            loadProvidersJob = null
            loadingForWebRTC = null
            return
        }

        // For direct connections, fetch all providers from server
        log.d { "Direct connection - fetching providers from server" }
        loadProvidersJob = viewModelScope.launch {
            try {
                authManager.getProviders()
                    .onSuccess { providerList ->
                        log.d { "Received ${providerList.size} providers: ${providerList.map { it.id }}" }
                        _providers.update { providerList }
                    }
                    .onFailure { error ->
                        log.e(error) { "Failed to load providers" }
                    }
            } finally {
                // Clear job reference when done (success or failure)
                loadProvidersJob = null
                loadingForWebRTC = null
            }
        }
    }

    fun login(provider: AuthProvider) {
        viewModelScope.launch {
            when (provider.type) {
                "builtin" -> {
                    authManager.loginWithCredentials(
                        provider.id,
                        username.value,
                        password.value,
                    )
                }

                else -> {
                    // OAuth or other redirect-based auth
                    // Use custom URL scheme for reliable deep linking
                    val returnUrl = "musicassistant://auth/callback"
                    authManager.getOAuthUrl(provider.id, returnUrl)
                        .onSuccess { url -> authManager.startOAuthFlow(url) }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // AuthenticationManager handles both flag setting and token clearing
            authManager.logout()
        }
    }

    private companion object {
        private val log = Logger.withTag("AuthVM")
    }
}
