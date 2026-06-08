package org.mochios.settings.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.settings.api.AccountApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController
import java.net.URLEncoder
import javax.inject.Inject

data class Identity(
    val entity: String = "",
    val fingerprint: String = "",
    val username: String = "",
    val name: String = "",
    val privacy: String = "private",
    val role: String = "",
)

/** Everything the screen needs to stream a built export bundle to storage via
 *  Android's DownloadManager: the public download URL, a friendly save name,
 *  and the bearer token (DownloadManager runs out-of-process, so it carries
 *  the Authorization header inline). */
data class ExportDownload(
    val url: String,
    val filename: String,
    val token: String?,
)

data class AccountUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: MochiError? = null,
    val identity: Identity = Identity(),
    val nameDraft: String = "",
)

/**
 * The Account screen is identity-only (name, username, fingerprint, directory
 * visibility). Login factors — methods, passkeys, authenticator, recovery
 * codes, third-party login — live on the separate Login screen
 * ([org.mochios.settings.ui.login.LoginScreen]), mirroring the web split.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val api: AccountApi,
    stepUpClient: SettingsStepUpClient,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    /** Origin of the Mochi server the session is bound to, and the settings-app
     *  bearer token. Captured here so the export download (handed to Android's
     *  DownloadManager, which runs outside the app's interceptor stack) can
     *  build an authenticated URL without the UI touching [SessionManager]. */
    private val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')
    private val token: String? = sessionManager.getTokenBlocking("settings")

    /** Step-up gate for closing the account and for building a data export —
     *  both are key-bearing or destructive, so they re-verify the login
     *  factors. Only one runs at a time. */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> _uiState.value = _uiState.value.copy(error = e.toMochiError()) },
    )

    // Emitted once the account has been marked for closure; the screen
    // observes this to sign out (sessions are already revoked server-side).
    private val _closed = MutableSharedFlow<Unit>()
    val closed: SharedFlow<Unit> = _closed.asSharedFlow()

    // Emitted once the export bundle is built; the screen hands the authenticated
    // URL to DownloadManager to stream the (possibly multi-GB) file to storage.
    private val _exportReady = MutableSharedFlow<ExportDownload>()
    val exportReady: SharedFlow<ExportDownload> = _exportReady.asSharedFlow()

    init {
        refresh()
    }

    /** Begin closure: re-verify, then call close. On success the account is
     *  soft-deleted with a grace period and every session is revoked. */
    fun closeAccount() {
        stepUp.request { token ->
            api.closeAccount(token).bodyOrThrow()
            _closed.emit(Unit)
        }
    }

    /** Begin a data export. The screen collects the passphrase first; this
     *  re-verifies the login factor(s), builds the encrypted bundle, then emits
     *  an authenticated download URL for the screen to enqueue. */
    fun exportData(passphrase: String) {
        val pass = passphrase.trim()
        if (pass.isEmpty()) return
        stepUp.request { stepUpToken ->
            val filename = api.exportData(stepUpToken, pass).bodyOrThrow().filename
            val url = serverUrl +
                "/settings/-/user/account/export/download?file=" +
                URLEncoder.encode(filename, "UTF-8")
            _exportReady.emit(ExportDownload(url = url, filename = filename, token = token))
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = api.getIdentity().bodyOrThrow()
                val identity = Identity(
                    entity = body.entity,
                    fingerprint = body.fingerprint,
                    username = body.username,
                    name = body.name,
                    privacy = body.privacy,
                    role = body.role,
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    identity = identity,
                    nameDraft = identity.name,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun updateName(draft: String) {
        _uiState.value = _uiState.value.copy(nameDraft = draft)
    }

    fun saveName() {
        val name = _uiState.value.nameDraft.trim()
        if (name.isBlank() || name == _uiState.value.identity.name) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                api.updateIdentity(name = name).bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    identity = _uiState.value.identity.copy(name = name),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    fun setPrivacy(privacy: String) {
        if (privacy != "public" && privacy != "private") return
        if (privacy == _uiState.value.identity.privacy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                api.updateIdentity(privacy = privacy).bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    identity = _uiState.value.identity.copy(privacy = privacy),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    private fun <T> retrofit2.Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }
}
