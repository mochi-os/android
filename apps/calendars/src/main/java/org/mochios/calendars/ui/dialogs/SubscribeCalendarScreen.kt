// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import org.mochios.android.api.MochiError
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.OAuthPkce
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.webUri
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ColorPicker
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiScaffold
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.NaturalCompare
import org.mochios.calendars.R
import org.mochios.calendars.model.CalendarAccount
import org.mochios.calendars.model.RemoteCalendar
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.repository.PermissionRequiredException
import org.mochios.calendars.ui.calendar.toColour
import javax.inject.Inject

/** The colour a new subscription starts on. */
private const val SUBSCRIPTION_COLOUR = "#a78bfa"

/** The colour a linked calendar starts on when its server names none. */
private const val LINKED_COLOUR = "#60a5fa"

/** What the wizard's first stage offers, in the order it lists them. */
enum class SubscribeKind {
    GOOGLE,
    APPLE,
    SERVER,
    ADDRESS,
}

/** How far through the wizard the user is. */
enum class SubscribeStage {
    KIND,
    CREDENTIAL,
    CALENDAR,
}

/**
 * The connected account a kind holds its calendars in, as the server types
 * them. The published address goes through no account at all, so it names
 * none.
 */
fun accountType(kind: SubscribeKind): String = when (kind) {
    SubscribeKind.GOOGLE -> CalendarAccount.TYPE_GOOGLE
    SubscribeKind.APPLE -> CalendarAccount.TYPE_APPLE
    SubscribeKind.SERVER -> CalendarAccount.TYPE_CALDAV
    SubscribeKind.ADDRESS -> ""
}

/** The stage one back from [stage], or null when the wizard itself closes. */
fun previous(stage: SubscribeStage): SubscribeStage? = when (stage) {
    SubscribeStage.KIND -> null
    SubscribeStage.CREDENTIAL -> SubscribeStage.KIND
    SubscribeStage.CALENDAR -> SubscribeStage.CREDENTIAL
}

/** The connected accounts of [kind], in the order the server listed them. */
fun matching(accounts: List<CalendarAccount>, kind: SubscribeKind): List<CalendarAccount> {
    val type = accountType(kind)
    return if (type.isEmpty()) emptyList() else accounts.filter { it.type == type }
}

/**
 * A consent the server asked for before it will fetch from the URL's host;
 * [url], [name] and [colour] replay the subscribe once it is granted.
 */
data class PendingPermission(
    val app: String,
    val permission: String,
    val label: String,
    val url: String,
    val name: String,
    val colour: String,
)

data class SubscribeUiState(
    val stage: SubscribeStage = SubscribeStage.KIND,
    val kind: SubscribeKind? = null,
    val accounts: List<CalendarAccount> = emptyList(),
    val providers: List<String> = emptyList(),
    val administrator: Boolean = false,
    val account: CalendarAccount? = null,
    val remote: List<RemoteCalendar> = emptyList(),
    val chosen: RemoteCalendar? = null,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val label: String = "",
    val name: String = "",
    val colour: String = SUBSCRIPTION_COLOUR,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val error: MochiError? = null,
    val permission: PendingPermission? = null,
    val finished: String? = null,
    /** A consent to open in the system browser, once. */
    val launch: String? = null,
)

/** What the Google step shows. */
enum class GoogleStep { MISSING, CONNECT, LIST }

/**
 * The Google step: the user's Google accounts when there are any; else an
 * offer to connect one, which the consent does; else, when the server holds
 * no Google client so no consent can be asked for, where the client goes.
 */
fun googleStep(accounts: List<CalendarAccount>, providers: List<String>): GoogleStep = when {
    accounts.isNotEmpty() -> GoogleStep.LIST
    providers.isEmpty() -> GoogleStep.MISSING
    else -> GoogleStep.CONNECT
}

@HiltViewModel
class SubscribeCalendarViewModel @Inject constructor(
    private val repository: CalendarsRepository,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscribeUiState())
    val uiState: StateFlow<SubscribeUiState> = _uiState.asStateFlow()

    init {
        // Read at once: the kinds on offer depend on what the server can
        // grant and what the user already holds.
        accounts()
        // A consent that came back while this screen was away is waiting
        // here, as the sign-in link's is for the login screen.
        viewModelScope.launch {
            sessionManager.oauthGrantReturn.collect { (code, error) ->
                if (error != null) {
                    sessionManager.clearOAuthGrantReturn()
                    sessionManager.consumeOAuthGrantVerifier()
                    _uiState.value = _uiState.value.copy(isBusy = false, error = MochiError.Local(R.string.calendars_grant_failed))
                } else if (code != null) {
                    sessionManager.clearOAuthGrantReturn()
                    completeGrant(code)
                }
            }
        }
    }

    /**
     * Asks Google for calendar access on [account], or on whichever account
     * the user picks when none is named. The consent runs in the system
     * browser: the verifier is held for the exchange, where the server lands
     * the grant against it and this app's token.
     */
    fun grant(account: CalendarAccount?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val verifier = OAuthPkce.generateVerifier()
                val challenge = OAuthPkce.challengeFor(verifier)
                val begun = repository.grant(account?.id.orEmpty(), if (account == null) CalendarAccount.TYPE_GOOGLE else "", challenge)
                // Recorded only once the server has answered, and in one
                // write with the nonce: a verifier left behind by a failed
                // begin is what an injected return needs.
                sessionManager.saveOAuthGrantVerifier(verifier, begun.nonce)
                _uiState.value = _uiState.value.copy(isBusy = false, launch = begun.url)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }

    fun consumeLaunch() {
        _uiState.value = _uiState.value.copy(launch = null)
    }

    /** The consent came back: land the grant, then open the account's calendars. */
    private fun completeGrant(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val verifier = sessionManager.consumeOAuthGrantVerifier()
                val token = sessionManager.getToken("calendars")
                if (verifier == null || token == null) {
                    _uiState.value = _uiState.value.copy(isBusy = false, error = MochiError.Local(R.string.calendars_grant_failed))
                    return@launch
                }
                val landed = authRepository.exchangeOAuthGrant(code, verifier, token)
                val answer = repository.listAccounts()
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    accounts = answer.accounts,
                    administrator = answer.administrator,
                    providers = answer.providers,
                )
                answer.accounts.firstOrNull { it.id == landed.account }?.let { open(it) }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }

    /** Stage one: what the user is adding decides what stage two asks for. */
    fun choose(kind: SubscribeKind) {
        _uiState.value = _uiState.value.copy(
            stage = SubscribeStage.CREDENTIAL,
            kind = kind,
            account = null,
            remote = emptyList(),
            chosen = null,
            url = "",
            username = "",
            password = "",
            label = "",
            name = "",
            colour = SUBSCRIPTION_COLOUR,
            error = null,
        )
        if (kind != SubscribeKind.ADDRESS) accounts()
    }

    /** The connected accounts a calendar can be linked through. */
    fun accounts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val answer = repository.listAccounts()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    accounts = answer.accounts,
                    administrator = answer.administrator,
                    providers = answer.providers,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /** Opens an account: what its server offers is read before the list shows. */
    fun open(account: CalendarAccount) {
        if (!account.calendars) return
        _uiState.value = _uiState.value.copy(
            stage = SubscribeStage.CALENDAR,
            account = account,
            remote = emptyList(),
            chosen = null,
            error = null,
        )
        remote(account.id)
    }

    private fun remote(account: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val calendars = repository.remoteCalendars(account)
                _uiState.value = _uiState.value.copy(isLoading = false, remote = calendars)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /** Reads the account's calendars again after a failure. */
    fun reload() {
        _uiState.value.account?.let { remote(it.id) }
    }

    fun setUrl(value: String) {
        _uiState.value = _uiState.value.copy(url = value, error = null)
    }

    fun setUsername(value: String) {
        _uiState.value = _uiState.value.copy(username = value, error = null)
    }

    fun setPassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun setLabel(value: String) {
        _uiState.value = _uiState.value.copy(label = value, error = null)
    }

    fun setName(value: String) {
        _uiState.value = _uiState.value.copy(name = value, error = null)
    }

    fun setColour(value: String) {
        _uiState.value = _uiState.value.copy(colour = value, error = null)
    }

    /**
     * Connects the account the form describes. The server tries it against
     * its own server first, so a wrong password fails here rather than at the
     * calendar list; the account that comes back opens the last stage.
     */
    fun connect() {
        val state = _uiState.value
        val kind = state.kind ?: return
        val type = accountType(kind)
        if (type.isEmpty() || state.isBusy) return
        if (state.username.isBlank() || state.password.isEmpty()) return
        if (kind == SubscribeKind.SERVER && state.url.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val account = repository.addAccount(
                    type,
                    state.url.trim(),
                    state.username.trim(),
                    state.password,
                    state.label.trim(),
                )
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    stage = SubscribeStage.CALENDAR,
                    accounts = _uiState.value.accounts + account,
                    account = account,
                    remote = emptyList(),
                    chosen = null,
                    password = "",
                    error = null,
                )
                remote(account.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }

    /** Opens the form on a remote calendar, filled from what its server calls it. */
    fun pick(calendar: RemoteCalendar) {
        _uiState.value = _uiState.value.copy(
            chosen = calendar,
            name = calendar.name,
            colour = calendar.colour.ifBlank { LINKED_COLOUR },
            error = null,
        )
    }

    /**
     * One step back, or null when there is none and the screen itself should
     * close. The last stage carries the form on top of the list of the
     * account's calendars, so the first step back there is to the list.
     */
    fun back(): SubscribeStage? {
        val state = _uiState.value
        if (state.stage == SubscribeStage.CALENDAR && state.chosen != null) {
            _uiState.value = state.copy(chosen = null, error = null)
            return SubscribeStage.CALENDAR
        }
        val stage = previous(state.stage) ?: return null
        _uiState.value = when (stage) {
            SubscribeStage.KIND -> state.copy(
                stage = stage,
                kind = null,
                account = null,
                remote = emptyList(),
                chosen = null,
                error = null,
            )
            SubscribeStage.CREDENTIAL -> state.copy(
                stage = stage,
                account = null,
                remote = emptyList(),
                chosen = null,
                error = null,
            )
            SubscribeStage.CALENDAR -> state.copy(stage = stage, error = null)
        }
        return stage
    }

    /** Links the calendar the form describes through the chosen account. */
    fun link() {
        val state = _uiState.value
        val account = state.account ?: return
        val chosen = state.chosen ?: return
        if (state.isBusy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val calendar = repository.linkCalendar(
                    account.id,
                    chosen.href,
                    state.name.trim(),
                    state.colour.trim().lowercase(),
                )
                _uiState.value = _uiState.value.copy(isBusy = false, finished = calendar.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }

    fun subscribe() {
        val state = _uiState.value
        if (state.url.isBlank() || state.isBusy) return
        subscribe(state.url.trim(), state.name.trim(), state.colour.trim().lowercase())
    }

    private fun subscribe(url: String, name: String, colour: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val calendar = repository.subscribeCalendar(url, name, colour)
                _uiState.value = _uiState.value.copy(isBusy = false, finished = calendar.id)
            } catch (e: PermissionRequiredException) {
                // Not a failure: the subscribe succeeds once the app may fetch
                // from that host, so ask and replay it.
                val label = runCatching { repository.permissionName(e.permission) }.getOrDefault(e.permission)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    permission = PendingPermission(e.app, e.permission, label, url, name, colour),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }

    fun allow() {
        val pending = _uiState.value.permission ?: return
        _uiState.value = _uiState.value.copy(permission = null, isBusy = true)
        viewModelScope.launch {
            try {
                repository.grantPermission(pending.app, pending.permission)
                subscribe(pending.url, pending.name, pending.colour)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }

    fun deny() {
        _uiState.value = _uiState.value.copy(permission = null)
    }
}

/**
 * Adds a calendar the user keeps somewhere else. The wizard asks what kind it
 * is, then for the account or address it lives behind, then which of the
 * account's calendars to mirror.
 *
 * A calendar reached through an account is linked: it is written to as the
 * user's own is, and the server carries each change both ways. A published
 * address is a subscription: the server fetches it on a schedule and it is
 * read-only here, and the first address on a new host needs the user's
 * consent, which the dialog below asks for.
 */
@Composable
fun SubscribeCalendarScreen(
    onBack: () -> Unit,
    onSubscribed: () -> Unit,
    viewModel: SubscribeCalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.finished) {
        if (uiState.finished != null) onSubscribed()
    }

    // The phone's own back gesture steps back through the wizard, as the top
    // bar's arrow does, rather than leaving at the first tap.
    BackHandler(enabled = uiState.stage != SubscribeStage.KIND) { viewModel.back() }

    val title = stringResource(R.string.calendars_subscribe)
    val back = { if (viewModel.back() == null) onBack() }

    if (uiState.stage == SubscribeStage.CREDENTIAL && uiState.kind == SubscribeKind.ADDRESS) {
        // The published address is a form and nothing else, so it wears the
        // create screens' chrome: the submit sits in the bottom bar.
        CreateEntityScaffold(
            title = title,
            submitLabel = stringResource(R.string.calendars_subscribe_submit),
            submitEnabled = uiState.url.isNotBlank() && !uiState.isBusy,
            isBusy = uiState.isBusy,
            error = uiState.error,
            onBack = back,
            onSubmit = viewModel::subscribe,
        ) { padding ->
            AddressForm(uiState, padding, viewModel::setUrl, viewModel::setName, viewModel::setColour)
        }
    } else {
        MochiScaffold(title = title, onBack = back) { padding ->
            when (uiState.stage) {
                SubscribeStage.KIND -> KindList(uiState, padding, viewModel::choose)
                SubscribeStage.CREDENTIAL -> Credential(uiState, padding, viewModel)
                SubscribeStage.CALENDAR -> if (uiState.chosen == null) {
                    RemoteList(uiState, padding, viewModel::pick, viewModel::reload)
                } else {
                    LinkForm(uiState, padding, viewModel::setName, viewModel::setColour, viewModel::link)
                }
            }
        }
    }

    uiState.permission?.let { pending ->
        MochiAlertDialog(
            onDismissRequest = viewModel::deny,
            title = stringResource(R.string.calendars_permission_title),
            confirmText = stringResource(R.string.calendars_permission_allow),
            onConfirm = viewModel::allow,
            dismissText = stringResource(R.string.calendars_permission_deny),
            onDismiss = viewModel::deny,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.calendars_permission_message, pending.app),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = pending.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            },
        )
    }
}

/** Stage one: the kinds of calendar the wizard can add here. */
@Composable
private fun KindList(uiState: SubscribeUiState, padding: PaddingValues, onChoose: (SubscribeKind) -> Unit) {
    val labels = SubscribeKind.entries.associateWith { stringResource(kindLabel(it)) }
    if (uiState.isLoading && uiState.accounts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding)) { Loading() }
        return
    }
    val kinds = kindsSorted(kindsOffered(uiState.accounts, uiState.providers, uiState.administrator)) { labels.getValue(it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(kinds, key = { it.name }) { kind ->
            MochiCard(onClick = { onChoose(kind) }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        kindIcon(kind),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(kindLabel(kind)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/**
 * The kinds worth offering: Google only when the server can grant an account,
 * when the user already holds one, or to an administrator, who can enable it.
 */
fun kindsOffered(accounts: List<CalendarAccount>, providers: List<String>, administrator: Boolean): List<SubscribeKind> {
    val google = "google" in providers || administrator || accounts.any { it.type == CalendarAccount.TYPE_GOOGLE }
    return SubscribeKind.entries.filter { it != SubscribeKind.GOOGLE || google }
}

/** The kinds in the order of their names in the user's language. */
fun kindsSorted(kinds: List<SubscribeKind> = SubscribeKind.entries.toList(), label: (SubscribeKind) -> String): List<SubscribeKind> =
    kinds.sortedWith(compareBy(NaturalCompare) { label(it) })

/** The glyph a kind wears: the two calendar services share one. */
private fun kindIcon(kind: SubscribeKind): ImageVector = when (kind) {
    SubscribeKind.GOOGLE, SubscribeKind.APPLE -> Icons.Outlined.CalendarMonth
    SubscribeKind.SERVER -> Icons.Outlined.Dns
    SubscribeKind.ADDRESS -> Icons.Outlined.RssFeed
}

/** What a kind is called. The service names are brands and do not translate. */
private fun kindLabel(kind: SubscribeKind): Int = when (kind) {
    SubscribeKind.GOOGLE -> R.string.calendars_subscribe_google
    SubscribeKind.APPLE -> R.string.calendars_subscribe_apple
    SubscribeKind.SERVER -> R.string.calendars_subscribe_server
    SubscribeKind.ADDRESS -> R.string.calendars_subscribe_address
}

/** Stage two: the account the calendars are reached through. */
@Composable
private fun Credential(
    uiState: SubscribeUiState,
    padding: PaddingValues,
    viewModel: SubscribeCalendarViewModel,
) {
    when (uiState.kind) {
        SubscribeKind.GOOGLE -> GoogleAccounts(uiState, padding, viewModel)
        SubscribeKind.APPLE, SubscribeKind.SERVER -> AccountForm(uiState, padding, viewModel)
        else -> Unit
    }
}

/**
 * The Google accounts already connected. One without calendar access, and a
 * user with no Google account at all, are offered Google's consent, which
 * opens in the system browser and comes back on the app's deep link.
 */
@Composable
private fun GoogleAccounts(
    uiState: SubscribeUiState,
    padding: PaddingValues,
    viewModel: SubscribeCalendarViewModel,
) {
    val accounts = matching(uiState.accounts, SubscribeKind.GOOGLE)
    val error = uiState.error
    val context = LocalContext.current
    LaunchedEffect(uiState.launch) {
        val url = uiState.launch ?: return@LaunchedEffect
        // The consent URL is the server's answer, and ACTION_VIEW dispatches
        // on the scheme, so only a web scheme may leave the app.
        webUri(url)?.let { target ->
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, target).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
        viewModel.consumeLaunch()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            uiState.isLoading && uiState.accounts.isEmpty() -> item("loading") { Loading() }
            error != null && uiState.accounts.isEmpty() -> item("error") {
                InlineErrorState(error = error, onRetry = viewModel::accounts)
            }
            else -> when (googleStep(accounts, uiState.providers)) {
                // No Google client is entered on this server, so no consent
                // can be asked for. Only an administrator reaches this.
                GoogleStep.MISSING -> item("missing") {
                    Note(stringResource(R.string.calendars_subscribe_google_enable))
                }
                // The server can grant one; the consent connects the account
                // the user picks at Google.
                GoogleStep.CONNECT -> item("empty") {
                    Column {
                        Note(stringResource(R.string.calendars_subscribe_google_connect))
                        Spacer(Modifier.height(12.dp))
                        MochiButton(
                            onClick = { viewModel.grant(null) },
                            enabled = !uiState.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.calendars_subscribe_google_connect_action))
                        }
                    }
                }
                GoogleStep.LIST -> items(accounts, key = { it.id }) { account ->
                    AccountRow(
                        account = account,
                        onOpen = { viewModel.open(account) },
                        onGrant = { viewModel.grant(account) },
                        busy = uiState.isBusy,
                    )
                }
            }
        }
    }
}

/**
 * The accounts of the kind already connected, and the form that connects
 * another: an Apple ID with an app-specific password, or a CalDAV server with
 * a login.
 */
@Composable
private fun AccountForm(
    uiState: SubscribeUiState,
    padding: PaddingValues,
    viewModel: SubscribeCalendarViewModel,
) {
    val kind = uiState.kind ?: return
    val server = kind == SubscribeKind.SERVER
    val accounts = matching(uiState.accounts, kind)
    CreateEntityForm(padding) {
        accounts.forEach { account ->
            AccountRow(account = account, onOpen = { viewModel.open(account) })
            Spacer(Modifier.height(8.dp))
        }
        if (accounts.isNotEmpty()) Spacer(Modifier.height(8.dp))
        if (server) {
            MochiTextField(
                value = uiState.url,
                onValueChange = viewModel::setUrl,
                label = { Text(stringResource(R.string.calendars_subscribe_server_url)) },
                placeholder = { Text(stringResource(R.string.calendars_subscribe_server_example)) },
                singleLine = true,
                enabled = !uiState.isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
        MochiTextField(
            value = uiState.username,
            onValueChange = viewModel::setUsername,
            label = {
                Text(
                    stringResource(
                        if (server) R.string.calendars_subscribe_username else R.string.calendars_subscribe_apple_id,
                    ),
                )
            },
            singleLine = true,
            enabled = !uiState.isBusy,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (server) KeyboardType.Text else KeyboardType.Email,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        MochiTextField(
            value = uiState.password,
            onValueChange = viewModel::setPassword,
            label = {
                Text(
                    stringResource(
                        if (server) {
                            R.string.calendars_subscribe_password
                        } else {
                            R.string.calendars_subscribe_password_apple
                        },
                    ),
                )
            },
            singleLine = true,
            enabled = !uiState.isBusy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        MochiTextField(
            value = uiState.label,
            onValueChange = viewModel::setLabel,
            label = { Text(stringResource(R.string.calendars_name)) },
            singleLine = true,
            enabled = !uiState.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        uiState.error?.let { failure ->
            Text(
                text = failure.userMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }
        MochiButton(
            onClick = viewModel::connect,
            enabled = uiState.username.isNotBlank() && uiState.password.isNotEmpty() &&
                (!server || uiState.url.isNotBlank()) && !uiState.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.calendars_subscribe_connect))
        }
    }
}

/**
 * One connected account: what the user called it over the address it signs in
 * as. An account without calendar access yet is shown but cannot be opened.
 */
@Composable
private fun AccountRow(
    account: CalendarAccount,
    onOpen: () -> Unit,
    onGrant: (() -> Unit)? = null,
    busy: Boolean = false,
) {
    val allowed = account.calendars
    MochiCard(
        onClick = onOpen,
        enabled = allowed,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = account.label.ifBlank { account.identifier },
                style = MaterialTheme.typography.bodyLarge,
                color = if (allowed) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (account.label.isNotBlank() && account.identifier.isNotBlank()) {
                Text(
                    text = account.identifier,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!allowed && onGrant != null) {
                Spacer(Modifier.height(8.dp))
                MochiButton(onClick = onGrant, enabled = !busy) {
                    Text(stringResource(R.string.calendars_link_grant))
                }
            }
        }
    }
}

/** Stage three: the calendars the account's server offers. */
@Composable
private fun RemoteList(
    uiState: SubscribeUiState,
    padding: PaddingValues,
    onPick: (RemoteCalendar) -> Unit,
    onRetry: () -> Unit,
) {
    val error = uiState.error
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            uiState.isLoading && uiState.remote.isEmpty() -> item("loading") { Loading() }
            error != null && uiState.remote.isEmpty() -> item("error") {
                InlineErrorState(error = error, onRetry = onRetry)
            }
            uiState.remote.isEmpty() -> item("empty") {
                Note(stringResource(R.string.calendars_link_remote_empty))
            }
            else -> items(uiState.remote, key = { it.href }) { calendar ->
                RemoteRow(calendar = calendar, onPick = { onPick(calendar) })
            }
        }
    }
}

/**
 * One calendar the account's server offers: its colour, its name and what its
 * server says about it. One already mirrored here says so and cannot be
 * linked a second time.
 */
@Composable
private fun RemoteRow(calendar: RemoteCalendar, onPick: () -> Unit) {
    val free = calendar.linked.isEmpty()
    MochiCard(
        onClick = onPick,
        enabled = free,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (calendar.colour.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(calendar.colour.toColour(MaterialTheme.colorScheme.primary)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = calendar.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (free) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (calendar.description.isNotBlank()) {
                    Text(
                        text = calendar.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!free) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.calendars_link_linked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What the linked calendar is called and wears here. */
@Composable
private fun LinkForm(
    uiState: SubscribeUiState,
    padding: PaddingValues,
    onName: (String) -> Unit,
    onColour: (String) -> Unit,
    onLink: () -> Unit,
) {
    CreateEntityForm(padding) {
        MochiTextField(
            value = uiState.name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.calendars_name)) },
            singleLine = true,
            enabled = !uiState.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        ColorPicker(
            hex = uiState.colour,
            onHexChange = onColour,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        uiState.error?.let { failure ->
            Text(
                text = failure.userMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }
        MochiButton(
            onClick = onLink,
            enabled = uiState.name.isNotBlank() && !uiState.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.calendars_link_submit))
        }
    }
}

/** The address of a published calendar, and what it is called here. */
@Composable
private fun AddressForm(
    uiState: SubscribeUiState,
    padding: PaddingValues,
    onUrl: (String) -> Unit,
    onName: (String) -> Unit,
    onColour: (String) -> Unit,
) {
    CreateEntityForm(padding) {
        MochiTextField(
            value = uiState.url,
            onValueChange = onUrl,
            label = { Text(stringResource(R.string.calendars_subscribe_url)) },
            singleLine = true,
            enabled = !uiState.isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        MochiTextField(
            value = uiState.name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.calendars_name)) },
            singleLine = true,
            enabled = !uiState.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        ColorPicker(
            hex = uiState.colour,
            onHexChange = onColour,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A line of plain text where a list would be. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
