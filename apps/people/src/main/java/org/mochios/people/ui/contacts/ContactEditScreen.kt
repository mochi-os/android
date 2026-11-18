// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.CompactTextField
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.LabeledSelectField
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiScaffold
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.SaveStatusIndicator
import org.mochios.android.util.providerName
import org.mochios.people.R
import org.mochios.people.api.HandleLookup
import org.mochios.people.model.Contact
import org.mochios.people.model.XmppContact
import org.mochios.people.model.friendState
import org.mochios.people.model.FriendState
import org.mochios.android.R as MochiR

/**
 * The contact editor, in create mode when the route names no contact and edit
 * mode when it does. An edit saves as it goes and has no Save; a new contact,
 * a copy as any other, is made by Create, after which the screen goes back.
 */
@Composable
fun ContactEditScreen(
    onBack: () -> Unit,
    /** Goes on as another contact: the one a merge left standing. */
    onOpen: (String) -> Unit,
    /** Opens a copy of the contact named, as a new contact. */
    onCopy: (String) -> Unit = {},
    onDeleted: () -> Unit,
    /** Opens the directory search to link this card to the person it finds. */
    onFindPerson: (contact: String, name: String) -> Unit = { _, _ -> },
    viewModel: ContactEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val form = uiState.form

    LaunchedEffect(uiState.opened) {
        uiState.opened?.let(onOpen)
    }
    LaunchedEffect(uiState.copied) {
        uiState.copied?.let(onCopy)
    }
    LaunchedEffect(uiState.left) {
        if (uiState.left) onBack()
    }
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onDeleted()
    }
    // Leaving an edit, by the back arrow or the system's back, saves first.
    BackHandler(enabled = !viewModel.creating) { viewModel.leave() }

    if (viewModel.creating) {
        CreateEntityScaffold(
            title = stringResource(R.string.people_contact_new_title),
            submitLabel = stringResource(R.string.people_contact_create),
            submitEnabled = form.valid && !uiState.isSaving,
            isBusy = uiState.isSaving,
            error = uiState.error,
            onBack = onBack,
            onSubmit = { viewModel.create() },
        ) { padding ->
            CreateEntityForm(padding) {
                ContactFields(
                    form = form,
                    books = uiState.books,
                    onChange = viewModel::updateForm,
                    lookups = uiState.handles,
                    onLookup = viewModel::lookup,
                )
            }
        }
    } else {
        EditScaffold(
            state = uiState,
            onBack = { viewModel.leave() },
            onLeave = { viewModel.save() },
            onRequestDelete = { viewModel.requestDelete() },
            onCopy = { viewModel.copy() },
            onMerge = { viewModel.openMerge() },
            onChange = viewModel::updateForm,
            onLookup = viewModel::lookup,
            onXmppToggle = viewModel::toggleXmpp,
            onFriendToggle = { on ->
                val contact = uiState.contact ?: return@EditScaffold
                when {
                    on && contact.person.isBlank() -> onFindPerson(contact.id, contact.name)
                    on -> viewModel.invite()
                    contact.friend -> viewModel.requestUnfriend()
                    else -> viewModel.cancelInvite()
                }
            },
        )
    }

    if (uiState.unfriendRequested) {
        val contact = uiState.contact
        // The confirmation stays up while the unfriend runs and after it
        // fails, saying why, so it can be tried again.
        MochiAlertDialog(
            onDismissRequest = { viewModel.cancelUnfriend() },
            title = stringResource(R.string.people_contacts_unfriend),
            confirmText = stringResource(R.string.people_contacts_unfriend),
            onConfirm = { viewModel.confirmUnfriend() },
            confirmLoading = uiState.isToggling,
            destructive = true,
            dismissText = stringResource(R.string.people_common_cancel),
        ) {
            Column {
                Text(stringResource(R.string.people_contacts_unfriend_confirm, contact?.name.orEmpty()))
                uiState.error?.let { failure ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = failure.userMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (uiState.deleteRequested) {
        val contact = uiState.contact
        MochiAlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = stringResource(R.string.people_contacts_delete),
            text = if (contact?.friend == true) {
                stringResource(R.string.people_contacts_delete_friend_confirm, contact.name)
            } else {
                stringResource(R.string.people_contacts_delete_confirm, contact?.name.orEmpty())
            },
            confirmText = stringResource(R.string.people_contacts_delete),
            onConfirm = { viewModel.confirmDelete() },
            confirmLoading = uiState.isDeleting,
            destructive = true,
            dismissText = stringResource(R.string.people_common_cancel),
        )
    }

    if (uiState.mergeOpen) {
        MergeDialog(
            contacts = uiState.mergeCandidates,
            pending = uiState.mergePending,
            failure = uiState.mergeError?.userMessage(),
            onPick = viewModel::merge,
            onDismiss = { viewModel.closeMerge() },
        )
    }

    val conflict = uiState.conflict
    if (conflict != null) {
        MochiAlertDialog(
            onDismissRequest = { viewModel.clearConflict() },
            title = stringResource(R.string.people_contact_conflict),
            text = conflict,
            confirmText = stringResource(MochiR.string.common_close),
            onConfirm = { viewModel.clearConflict() },
        )
    }
}

/**
 * The contacts to merge the one being edited with, filtered by name as typed.
 * A refused merge stays here, saying why, so another can be picked.
 */
@Composable
internal fun MergeDialog(
    contacts: List<Contact>,
    pending: String?,
    failure: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val query = search.trim()
    val shown = if (query.isBlank()) {
        contacts
    } else {
        contacts.filter {
            it.name.contains(query, ignoreCase = true) || it.directory.contains(query, ignoreCase = true)
        }
    }
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.people_contact_merge_title),
        dismissText = stringResource(R.string.people_common_cancel),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MochiTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.people_contacts_search_placeholder)) },
                singleLine = true,
            )
            failure?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.people_contacts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = pending == null) { onPick(contact.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                                if (contact.directory.isNotBlank() && contact.directory != contact.name) {
                                    Text(
                                        text = contact.directory,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (pending == contact.id) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The friendship handshake as a switch: off for a plain contact, on once a
 * friend, and on with "Invited" beneath it while the other side has not
 * answered. It drives the handshake and never sets the flag by hand.
 */
@Composable
private fun FriendSwitch(
    state: FriendState,
    /** Where the switch was moved to, while that change is on its way. */
    moving: Boolean?,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.people_contact_friend),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state == FriendState.INVITED) {
                Text(
                    text = stringResource(R.string.people_contacts_invited),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = moving ?: (state != FriendState.NONE),
            onCheckedChange = onToggle,
        )
    }
}

/**
 * The contact on XMPP through one of the user's hosted XMPP accounts: on
 * while the account sees their presence or has asked to, with "Invited" while
 * the ask is unanswered, and which address through which account once there
 * is more than one of either.
 */
@Composable
internal fun XmppSwitch(
    entry: XmppContact,
    several: Boolean,
    /** Where the switch was moved to, while that change is on its way. */
    moving: Boolean?,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.people_contact_xmpp),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (entry.invited) {
                Text(
                    text = stringResource(R.string.people_contacts_invited),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (several) {
                Text(
                    text = "${entry.address} · ${entry.identifier}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = moving ?: entry.on,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun EditScaffold(
    state: ContactEditUiState,
    onBack: () -> Unit,
    /** A field was left: the editor saves it. */
    onLeave: () -> Unit,
    onRequestDelete: () -> Unit,
    onCopy: () -> Unit,
    onMerge: () -> Unit,
    onChange: (ContactForm) -> Unit,
    onLookup: (String) -> Unit,
    onXmppToggle: (XmppContact, Boolean) -> Unit,
    onFriendToggle: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    MochiScaffold(
        title = state.contact?.name?.ifBlank { null }
            ?: stringResource(R.string.people_contact_edit_title),
        onBack = onBack,
        actions = {
            // An edit saves as it goes: say so, as typing rests and the save lands.
            SaveStatusIndicator(state.save, modifier = Modifier.padding(horizontal = 4.dp))
            Box {
                MochiIconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.people_contacts_actions),
                    )
                }
                MochiDropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(MochiR.string.common_copy)) },
                        onClick = {
                            menuOpen = false
                            onCopy()
                        },
                    )
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.people_contact_merge)) },
                        onClick = {
                            menuOpen = false
                            onMerge()
                        },
                    )
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(MochiR.string.common_delete)) },
                        onClick = {
                            menuOpen = false
                            onRequestDelete()
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (state.isLoading && state.contact == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LoadingState()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                state.error?.let { failure ->
                    Text(
                        text = failure.userMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                CompositionLocalProvider(LocalFieldLeft provides onLeave) {
                    ContactFields(
                        form = state.form,
                        books = state.books,
                        onChange = onChange,
                        lookups = state.handles,
                        onLookup = onLookup,
                        xmpp = state.contact?.xmpp.orEmpty(),
                        onEndXmpp = { listed -> onXmppToggle(listed, false) },
                        action = state.contact?.let { contact ->
                            @Composable {
                                Column {
                                    // A switch moves when tapped and keeps its look while
                                    // its change goes; one tapped meanwhile is not taken.
                                    FriendSwitch(
                                        state = contact.friendState(state.sent),
                                        moving = state.switching[FRIEND_SWITCH],
                                        onToggle = { on -> if (!state.isToggling) onFriendToggle(on) },
                                    )
                                    for (entry in contact.xmpp) {
                                        XmppSwitch(
                                            entry = entry,
                                            several = contact.xmpp.size > 1,
                                            moving = state.switching[entry.key],
                                            onToggle = { on -> if (!state.isToggling) onXmppToggle(entry, on) },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** What leaving a field does: in an edit, the editor saves it. */
internal val LocalFieldLeft = staticCompositionLocalOf<() -> Unit> { {} }

/** Calls [LocalFieldLeft] when this field loses the focus it had. */
internal fun Modifier.leaving(): Modifier = composed {
    val left = LocalFieldLeft.current
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { state ->
        if (focused && !state.isFocused) left()
        focused = state.isFocused
    }
}

/** A kind of value the Add menu gives a contact: its glyph, its name and the form with one more. */
private class Addition(val icon: ImageVector, val label: Int, val add: (ContactForm) -> ContactForm)

private val ADDITIONS = listOf(
    Addition(Icons.Outlined.Email, R.string.people_contact_email) { form ->
        form.copy(emails = form.emails + TypedEntry(type = TYPE_HOME))
    },
    Addition(Icons.Outlined.Phone, R.string.people_contact_phone) { form ->
        form.copy(phones = form.phones + TypedEntry(type = TYPE_MOBILE))
    },
    Addition(Icons.Outlined.Place, R.string.people_contact_address) { form ->
        form.copy(addresses = form.addresses + AddressEntry(type = TYPE_HOME))
    },
    Addition(Icons.Outlined.AlternateEmail, R.string.people_contact_handle) { form ->
        form.copy(handles = form.handles + HandleEntry())
    },
    Addition(Icons.AutoMirrored.Outlined.Chat, R.string.people_contact_messaging) { form ->
        form.copy(messaging = form.messaging + MessagingEntry())
    },
)

/** The XMPP contact a messaging row names, while it is on the contact list there. */
internal fun xmppListed(xmpp: List<XmppContact>, entry: MessagingEntry): XmppContact? {
    if (entry.network != NETWORK_XMPP) return null
    val address = entry.value.trim().removePrefix("xmpp:")
    return xmpp.firstOrNull { listed -> listed.on && listed.address.equals(address, ignoreCase = true) }
}

/** A single field's name and glyph, as the Add menu and the field show them. */
private fun Single.label(): Int = when (this) {
    Single.NICKNAME -> R.string.people_contact_nickname
    Single.BIRTHDAY -> R.string.people_contact_birthday
    Single.ORGANISATION -> R.string.people_contact_organisation
    Single.TITLE -> R.string.people_contact_title
    Single.URL -> R.string.people_contact_url
    Single.NOTE -> R.string.people_contact_note
}

private fun Single.icon(): ImageVector = when (this) {
    Single.NICKNAME -> Icons.Outlined.Person
    Single.BIRTHDAY -> Icons.Outlined.Cake
    Single.ORGANISATION -> Icons.Outlined.Business
    Single.TITLE -> Icons.Outlined.Work
    Single.URL -> Icons.Outlined.Link
    Single.NOTE -> Icons.Outlined.StickyNote2
}

/**
 * One Add button whose menu offers every kind of value a contact can gain,
 * and each single field the contact does not show yet.
 */
@Composable
private fun AddMenu(
    form: ContactForm,
    onChange: (ContactForm) -> Unit,
    hidden: List<Single>,
    onReveal: (Single) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        MochiTextButton(onClick = { open = true }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(MochiR.string.common_add))
        }
        MochiDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ADDITIONS.forEach { addition ->
                MochiDropdownMenuItem(
                    text = { Text(stringResource(addition.label)) },
                    leadingIcon = { Icon(addition.icon, contentDescription = null) },
                    onClick = {
                        open = false
                        onChange(addition.add(form))
                    },
                )
            }
            hidden.forEach { single ->
                MochiDropdownMenuItem(
                    text = { Text(stringResource(single.label())) },
                    leadingIcon = { Icon(single.icon(), contentDescription = null) },
                    onClick = {
                        open = false
                        onReveal(single)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ContactFields(
    form: ContactForm,
    books: List<org.mochios.people.model.Book>,
    onChange: (ContactForm) -> Unit,
    action: (@Composable () -> Unit)? = null,
    /** What the networks show of the handles typed, by handle. */
    lookups: Map<String, HandleLookup> = emptyMap(),
    /** Asks the network what it shows of a handle. */
    onLookup: (String) -> Unit = {},
    /** The contact on XMPP through the user's accounts, by address. */
    xmpp: List<XmppContact> = emptyList(),
    /** Takes the contact off an XMPP account's contact list. */
    onEndXmpp: (XmppContact) -> Unit = {},
) {
    val emailTypes = EMAIL_TYPES.map { type -> type to stringResource(typeLabel(type)) }
    val phoneTypes = PHONE_TYPES.map { type -> type to stringResource(typeLabel(type)) }
    val addressTypes = ADDRESS_TYPES.map { type -> type to stringResource(typeLabel(type)) }

    // The single fields shown: those the card fills, as it is read, and those
    // added from the Add menu, each until it is removed.
    val filled = form.filled()
    var added by remember { mutableStateOf(emptySet<Single>()) }
    LaunchedEffect(filled) {
        if (!added.containsAll(filled)) added = added + filled
    }
    val shown = added + filled
    var ending by remember { mutableStateOf<Pair<Int, XmppContact>?>(null) }
    val remove = { single: Single ->
        added = added - single
        onChange(form.emptied(single))
    }

    if (action != null) {
        action()
        Spacer(modifier = Modifier.height(8.dp))
    }
    Column {
        LabelledField(stringResource(R.string.people_contact_name)) {
            CompactTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = it)) },
                modifier = Modifier.fillMaxWidth().leaving(),
            )
        }
        LabelledField(stringResource(R.string.people_contact_given)) {
            CompactTextField(
                value = form.given,
                onValueChange = { onChange(form.copy(given = it)) },
                modifier = Modifier.fillMaxWidth().leaving(),
            )
        }
        LabelledField(stringResource(R.string.people_contact_family)) {
            CompactTextField(
                value = form.family,
                onValueChange = { onChange(form.copy(family = it)) },
                modifier = Modifier.fillMaxWidth().leaving(),
            )
        }
        if (Single.NICKNAME in shown) {
            RemovableField(Single.NICKNAME, onRemove = remove) {
                CompactTextField(
                    value = form.nickname,
                    onValueChange = { onChange(form.copy(nickname = it)) },
                    modifier = Modifier.fillMaxWidth().leaving(),
                )
            }
        }
    }

    // Emails, telephones, addresses, handles and messaging addresses share one
    // block: an Add menu offering each kind, then only the rows that exist,
    // each marked by its kind's glyph. One button never wraps, as five did.
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(4.dp))
    AddMenu(
        form = form,
        onChange = onChange,
        hidden = Single.entries.filter { single -> single !in shown },
        onReveal = { single -> added = added + single },
    )
    Column {
        form.emails.forEachIndexed { index, entry ->
            TypedEntryRow(
                icon = Icons.Outlined.Email,
                entry = entry,
                types = emailTypes,
                keyboardType = KeyboardType.Email,
                placeholder = stringResource(R.string.people_contact_email),
                onChange = { updated ->
                    onChange(form.copy(emails = form.emails.replaced(index, updated)))
                },
                onRemove = {
                    onChange(form.copy(emails = form.emails.without(index)))
                },
            )
        }
        form.phones.forEachIndexed { index, entry ->
            TypedEntryRow(
                icon = Icons.Outlined.Phone,
                entry = entry,
                types = phoneTypes,
                keyboardType = KeyboardType.Phone,
                placeholder = stringResource(R.string.people_contact_phone),
                onChange = { updated ->
                    onChange(form.copy(phones = form.phones.replaced(index, updated)))
                },
                onRemove = {
                    onChange(form.copy(phones = form.phones.without(index)))
                },
            )
        }
        form.addresses.forEachIndexed { index, address ->
            AddressRows(
                icon = Icons.Outlined.Place,
                address = address,
                types = addressTypes,
                onChange = { updated ->
                    onChange(form.copy(addresses = form.addresses.replaced(index, updated)))
                },
                onRemove = {
                    onChange(form.copy(addresses = form.addresses.without(index)))
                },
            )
        }
        form.handles.forEachIndexed { index, handle ->
            HandleRow(
                handle = handle,
                found = lookups[handle.value.trim()],
                onLookup = onLookup,
                onChange = { updated ->
                    onChange(form.copy(handles = form.handles.replaced(index, updated)))
                },
                onRemove = {
                    onChange(form.copy(handles = form.handles.without(index)))
                },
            )
        }
        form.messaging.forEachIndexed { index, entry ->
            MessagingRow(
                entry = entry,
                onChange = { updated ->
                    onChange(form.copy(messaging = form.messaging.replaced(index, updated)))
                },
                onRemove = {
                    val listed = xmppListed(xmpp, entry)
                    if (listed != null) ending = index to listed
                    else onChange(form.copy(messaging = form.messaging.without(index)))
                },
            )
        }
    }
    // An XMPP address on one of the user's contact lists: removing it from
    // the card asks whether to take them off the list there too, keeping them
    // unless told.
    ending?.let { (index, listed) ->
        val drop = { end: Boolean ->
            ending = null
            onChange(form.copy(messaging = form.messaging.without(index)))
            if (end) onEndXmpp(listed)
        }
        MochiAlertDialog(
            onDismissRequest = { drop(false) },
            title = stringResource(R.string.people_contact_xmpp),
            text = listed.address,
            confirmText = stringResource(R.string.people_contact_remove),
            onConfirm = { drop(true) },
            destructive = true,
            dismissText = stringResource(R.string.people_contact_keep),
        )
    }

    // Details: the book, and the single fields shown under them.
    val details = listOf(Single.BIRTHDAY, Single.ORGANISATION, Single.TITLE, Single.URL, Single.NOTE).filter { it in shown }
    if (books.isEmpty() && details.isEmpty()) return
    SectionHeading(title = stringResource(R.string.people_contact_section_detail))
    Column {
        if (books.isNotEmpty()) {
            LabeledSelectField(
                label = stringResource(R.string.people_contact_book),
                options = books.map { book -> book.id to book.name },
                selected = form.book,
                onSelect = { onChange(form.copy(book = it)) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        for (single in details) {
            RemovableField(single, onRemove = remove) {
                when (single) {
                    Single.BIRTHDAY -> BirthdayField(
                        value = form.birthday,
                        onValueChange = { value, invalid ->
                            onChange(form.copy(birthday = value ?: form.birthday, birthdayInvalid = invalid))
                        },
                    )
                    Single.ORGANISATION -> CompactTextField(
                        value = form.organisation,
                        onValueChange = { onChange(form.copy(organisation = it)) },
                        modifier = Modifier.fillMaxWidth().leaving(),
                    )
                    Single.TITLE -> CompactTextField(
                        value = form.title,
                        onValueChange = { onChange(form.copy(title = it)) },
                        modifier = Modifier.fillMaxWidth().leaving(),
                    )
                    Single.URL -> CompactTextField(
                        value = form.url.value,
                        onValueChange = { onChange(form.copy(url = form.url.copy(value = it))) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().leaving(),
                    )
                    Single.NOTE -> MochiTextField(
                        value = form.note,
                        onValueChange = { onChange(form.copy(note = it)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().leaving(),
                    )
                    Single.NICKNAME -> Unit
                }
            }
        }
    }
}

/** A single field under its name, with the button that removes it. */
@Composable
private fun RemovableField(single: Single, onRemove: (Single) -> Unit, field: @Composable () -> Unit) {
    LabelledField(stringResource(single.label())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) { field() }
            MochiIconButton(onClick = { onRemove(single) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.people_contact_remove))
            }
        }
    }
}

/**
 * A section's heading line on the page: a rule above it, its title, and the
 * section's action at the end. The first section has no rule.
 */
@Composable
private fun SectionHeading(
    title: String,
    action: (@Composable () -> Unit)? = null,
    divider: Boolean = true,
) {
    if (divider) {
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** The glyph of a row's kind, since emails, telephones and addresses share one block. */
@Composable
private fun KindIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
}

/** A field with its name above it, the shape every row in the editor takes. */
@Composable
private fun LabelledField(label: String, field: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        field()
    }
}

@Composable
private fun TypedEntryRow(
    icon: ImageVector,
    entry: TypedEntry,
    types: List<Pair<String, String>>,
    keyboardType: KeyboardType,
    placeholder: String,
    onChange: (TypedEntry) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindIcon(icon)
            CompactTextField(
                value = entry.value,
                onValueChange = { onChange(entry.copy(value = it)) },
                placeholder = placeholder,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.weight(1f).leaving(),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                LabeledSelectField(
                    label = stringResource(R.string.people_contact_type),
                    options = types,
                    selected = entry.type,
                    onSelect = { onChange(entry.copy(type = it)) },
                )
            }
            MochiIconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.people_contact_remove),
                )
            }
        }
    }
}

/** The example each network's handle field shows, as the network writes one. */
@Composable
private fun handleExample(network: String): String = stringResource(
    if (network == NETWORK_BLUESKY) R.string.people_contact_example_bluesky else R.string.people_contact_example_fediverse,
)

/**
 * A handle on another network: the handle, its network, and what the network
 * shows of the person once the handle resolves, so a typo is seen before the
 * card is saved. A handle is asked of its network once typing rests.
 */
@Composable
private fun HandleRow(
    handle: HandleEntry,
    found: HandleLookup?,
    onLookup: (String) -> Unit,
    onChange: (HandleEntry) -> Unit,
    onRemove: () -> Unit,
) {
    LaunchedEffect(handle.value) {
        delay(LOOKUP_PAUSE)
        onLookup(handle.value)
    }
    val typing = remember { FocusRequester() }
    var chosen by remember { mutableStateOf(0) }
    LaunchedEffect(chosen) { if (chosen > 0) typing.requestFocus() }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        // The network first, then the handle written as it writes them, as on
        // the web: chosen below the field, the network seemed to do nothing.
        NetworkLine(
            options = HANDLE_NETWORKS.map { network -> network to providerName(network) },
            selected = handle.network,
            onSelect = { network ->
                onChange(handle.copy(network = network))
                if (handle.value.isBlank()) chosen++
            },
            onRemove = onRemove,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindIcon(Icons.Outlined.AlternateEmail)
            CompactTextField(
                value = handle.value,
                onValueChange = { onChange(handle.copy(value = it)) },
                placeholder = handleExample(handle.network),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.weight(1f).focusRequester(typing).leaving(),
            )
        }
        if (found != null) {
            Row(
                modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EntityAvatar(name = found.name, src = found.avatar.ifBlank { null }, size = 20.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = listOf(found.name, found.address).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A row's network, chosen first, and the button that removes the row. */
@Composable
private fun NetworkLine(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            LabeledSelectField(
                label = stringResource(R.string.people_contact_network),
                options = options,
                selected = selected,
                onSelect = onSelect,
            )
        }
        MochiIconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.people_contact_remove),
            )
        }
    }
}

/**
 * A messaging address: the address, written with the scheme its network
 * takes, and its network.
 */
@Composable
private fun MessagingRow(
    entry: MessagingEntry,
    onChange: (MessagingEntry) -> Unit,
    onRemove: () -> Unit,
) {
    val other = stringResource(R.string.people_contact_network_other)
    val typing = remember { FocusRequester() }
    var chosen by remember { mutableStateOf(0) }
    LaunchedEffect(chosen) { if (chosen > 0) typing.requestFocus() }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        // The network first, then the address written as it writes them, as
        // on the web: chosen below the field, the network seemed to do nothing.
        NetworkLine(
            options = MESSAGING_NETWORKS.map { network ->
                network to if (network == NETWORK_OTHER) other else providerName(network)
            },
            selected = entry.network,
            onSelect = { network ->
                onChange(entry.copy(network = network))
                if (entry.value.isBlank()) chosen++
            },
            onRemove = onRemove,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindIcon(Icons.AutoMirrored.Outlined.Chat)
            CompactTextField(
                value = entry.value,
                onValueChange = { onChange(entry.copy(value = it)) },
                placeholder = when (entry.network) {
                    NETWORK_FEDIVERSE -> stringResource(R.string.people_contact_example_fediverse)
                    NETWORK_MATRIX -> stringResource(R.string.people_contact_example_matrix)
                    NETWORK_XMPP -> stringResource(R.string.people_contact_example_xmpp)
                    else -> ""
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.weight(1f).focusRequester(typing).leaving(),
            )
        }
    }
}

/** How long typing in a handle rests before it is asked of its network. */
private const val LOOKUP_PAUSE = 600L

@Composable
private fun AddressRows(
    icon: ImageVector,
    address: AddressEntry,
    types: List<Pair<String, String>>,
    onChange: (AddressEntry) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindIcon(icon)
            CompactTextField(
                value = address.street,
                onValueChange = { onChange(address.copy(street = it)) },
                placeholder = stringResource(R.string.people_contact_street),
                modifier = Modifier.weight(1f).leaving(),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                LabeledSelectField(
                    label = stringResource(R.string.people_contact_type),
                    options = types,
                    selected = address.type,
                    onSelect = { onChange(address.copy(type = it)) },
                )
            }
            MochiIconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.people_contact_remove),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        CompactTextField(
            value = address.city,
            onValueChange = { onChange(address.copy(city = it)) },
            placeholder = stringResource(R.string.people_contact_city),
            modifier = Modifier.fillMaxWidth().leaving(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        CompactTextField(
            value = address.region,
            onValueChange = { onChange(address.copy(region = it)) },
            placeholder = stringResource(R.string.people_contact_region),
            modifier = Modifier.fillMaxWidth().leaving(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        CompactTextField(
            value = address.postcode,
            onValueChange = { onChange(address.copy(postcode = it)) },
            placeholder = stringResource(R.string.people_contact_postcode),
            modifier = Modifier.fillMaxWidth().leaving(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        CompactTextField(
            value = address.country,
            onValueChange = { onChange(address.copy(country = it)) },
            placeholder = stringResource(R.string.people_contact_country),
            modifier = Modifier.fillMaxWidth().leaving(),
        )
    }
}

/**
 * `BDAY` as an ISO date behind the platform date picker, which is given a
 * locale that produces the user's own first day of the week — Material's
 * picker takes no first-day parameter of its own.
 */
private fun typeLabel(type: String): Int = when (type) {
    TYPE_WORK -> R.string.people_contact_type_work
    TYPE_MOBILE -> R.string.people_contact_type_mobile
    TYPE_OTHER -> R.string.people_contact_type_other
    else -> R.string.people_contact_type_home
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> =
    mapIndexed { position, existing -> if (position == index) value else existing }

private fun <T> List<T>.without(index: Int): List<T> =
    filterIndexed { position, _ -> position != index }
