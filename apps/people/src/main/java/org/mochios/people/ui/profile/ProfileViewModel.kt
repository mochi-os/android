package org.mochios.people.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.people.model.PersonInformation
import org.mochios.people.model.PersonStyle
import org.mochios.people.repository.PeopleRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Slot identifier — mirrors the web `slot: 'avatar' | 'banner' | 'favicon'`
 * argument. Used by the screen to drive the [ImagePickerDialog] resize/cap
 * heuristics and pick the matching upload mutator.
 */
enum class ImageSlot { AVATAR, BANNER, FAVICON }

/**
 * Which text field is currently being saved. Lets each section's Save control
 * show its own in-flight spinner instead of every button reacting to a single
 * shared flag.
 */
enum class ProfileField { NAME, BIO, ACCENT, PRIVACY }

/**
 * Reactive state for the Profile editor. Each `*Draft` field holds the
 * unsaved-but-being-edited value; `info` holds the last value we successfully
 * round-tripped through the server. The screen renders drafts everywhere
 * except the preview card's fingerprint / identity, which always shows the
 * canonical server value.
 *
 * `savingSlot` tracks per-slot image uploads and `savingField` tracks per-field
 * text saves, so the relevant control can show an in-flight indicator without
 * disabling the whole form.
 */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val savingField: ProfileField? = null,
    val savingSlot: ImageSlot? = null,
    val info: PersonInformation? = null,
    val nameDraft: String = "",
    val bioDraft: String = "",
    val accentDraft: String = "",
    val privacyDraft: String = "private",
    val error: MochiError? = null,
) {
    /** True while any field save is in flight. */
    val isSaving: Boolean get() = savingField != null
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PeopleRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    /** Server origin (no trailing slash) for building avatar / banner URLs. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val person = sessionManager.getBoundIdentity()
                    ?: throw IllegalStateException("no bound identity")
                val info = repository.getPersonInformation(person)
                _uiState.value = ProfileUiState(
                    isLoading = false,
                    info = info,
                    nameDraft = info.name,
                    bioDraft = info.profile,
                    accentDraft = info.style.accent.orEmpty(),
                    privacyDraft = info.privacy.ifBlank { "private" },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    // ---- Field drafts ----

    fun setNameDraft(value: String) {
        _uiState.value = _uiState.value.copy(nameDraft = value)
    }

    fun setBioDraft(value: String) {
        _uiState.value = _uiState.value.copy(bioDraft = value)
    }

    fun setAccentDraft(value: String) {
        _uiState.value = _uiState.value.copy(accentDraft = value)
    }

    fun setPrivacyDraft(value: String) {
        _uiState.value = _uiState.value.copy(privacyDraft = value)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ---- Mutations ----

    fun saveName(onSuccess: () -> Unit = {}, onError: (MochiError) -> Unit = {}) {
        val trimmed = _uiState.value.nameDraft.trim()
        val info = _uiState.value.info ?: return
        if (trimmed.isEmpty() || trimmed == info.name) return
        launchSave(ProfileField.NAME, onSuccess, onError) { person ->
            repository.setName(person, trimmed)
            val updated = info.copy(name = trimmed)
            _uiState.value = _uiState.value.copy(info = updated, nameDraft = trimmed)
        }
    }

    fun saveBio(onSuccess: () -> Unit = {}, onError: (MochiError) -> Unit = {}) {
        val info = _uiState.value.info ?: return
        val value = _uiState.value.bioDraft
        if (value == info.profile) return
        if (value.length > BIO_MAX) {
            onError(MochiError.Unknown("Profile too long"))
            return
        }
        launchSave(ProfileField.BIO, onSuccess, onError) { person ->
            repository.setProfile(person, value)
            val updated = info.copy(profile = value)
            _uiState.value = _uiState.value.copy(info = updated, bioDraft = value)
        }
    }

    fun saveAccent(onSuccess: () -> Unit = {}, onError: (MochiError) -> Unit = {}) {
        val info = _uiState.value.info ?: return
        val trimmed = _uiState.value.accentDraft.trim()
        if (trimmed == info.style.accent.orEmpty()) return
        if (trimmed.isNotEmpty() && !ACCENT_PATTERN.matches(trimmed)) {
            onError(MochiError.Unknown("Invalid colour"))
            return
        }
        launchSave(ProfileField.ACCENT, onSuccess, onError) { person ->
            repository.setAccent(person, trimmed)
            val updated = info.copy(style = PersonStyle(accent = trimmed.ifEmpty { null }))
            _uiState.value = _uiState.value.copy(info = updated, accentDraft = trimmed)
        }
    }

    /**
     * Clear the accent by posting `accent=""` to the style endpoint. No-ops
     * when nothing is set so an empty profile doesn't fire a needless request.
     */
    fun clearAccent(onSuccess: () -> Unit = {}, onError: (MochiError) -> Unit = {}) {
        val info = _uiState.value.info ?: return
        if (info.style.accent.orEmpty().isEmpty() && _uiState.value.accentDraft.isEmpty()) return
        launchSave(ProfileField.ACCENT, onSuccess, onError) { person ->
            repository.setAccent(person, "")
            val updated = info.copy(style = PersonStyle(accent = null))
            _uiState.value = _uiState.value.copy(info = updated, accentDraft = "")
        }
    }

    fun savePrivacy(value: String, onError: (MochiError) -> Unit = {}) {
        if (value != "public" && value != "private") return
        val info = _uiState.value.info ?: return
        if (value == info.privacy) return
        setPrivacyDraft(value)
        launchSave(ProfileField.PRIVACY, onSuccess = {}, onError) { person ->
            repository.setPrivacy(person, value)
            val updated = info.copy(privacy = value)
            _uiState.value = _uiState.value.copy(info = updated, privacyDraft = value)
        }
    }

    fun uploadAvatar(bytes: ByteArray) = uploadSlot(ImageSlot.AVATAR, bytes)
    fun uploadBanner(bytes: ByteArray) = uploadSlot(ImageSlot.BANNER, bytes)
    fun uploadFavicon(bytes: ByteArray) = uploadSlot(ImageSlot.FAVICON, bytes)

    private fun uploadSlot(slot: ImageSlot, bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(savingSlot = slot, error = null)
            val file = File(context.cacheDir, "${slot.name.lowercase()}-${UUID.randomUUID()}.jpg")
            try {
                file.writeBytes(bytes)
                val person = sessionManager.getBoundIdentity()
                    ?: throw IllegalStateException("no bound identity")
                when (slot) {
                    ImageSlot.AVATAR -> repository.setAvatar(person, file)
                    ImageSlot.BANNER -> repository.setBanner(person, file)
                    ImageSlot.FAVICON -> repository.setFavicon(person, file)
                }
                // Refresh to pick up the new attachment id (used in the
                // avatar/banner cache-busting URLs).
                val info = repository.getPersonInformation(person)
                _uiState.value = _uiState.value.copy(
                    info = info,
                    savingSlot = null,
                    nameDraft = _uiState.value.nameDraft,
                    bioDraft = _uiState.value.bioDraft,
                    accentDraft = _uiState.value.accentDraft,
                    privacyDraft = _uiState.value.privacyDraft,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    savingSlot = null,
                    error = e.toMochiError(),
                )
            } finally {
                file.delete()
            }
        }
    }

    private fun launchSave(
        field: ProfileField,
        onSuccess: () -> Unit,
        onError: (MochiError) -> Unit,
        block: suspend (person: String) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(savingField = field, error = null)
            try {
                val person = sessionManager.getBoundIdentity()
                    ?: throw IllegalStateException("no bound identity")
                block(person)
                _uiState.value = _uiState.value.copy(savingField = null)
                onSuccess()
            } catch (e: Exception) {
                val err = e.toMochiError()
                _uiState.value = _uiState.value.copy(savingField = null, error = err)
                onError(err)
            }
        }
    }

    companion object {
        const val BIO_MAX = 100 * 1024 // 100KB cap (web uses 100*100 chars; spec asks for 100KB)
        val ACCENT_PATTERN = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
    }
}
