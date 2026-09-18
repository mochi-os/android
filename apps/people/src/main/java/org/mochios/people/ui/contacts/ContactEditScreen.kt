// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.CompactTextField
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.LabeledSelectField
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiScaffold
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.Section
import org.mochios.people.R
import org.mochios.android.R as MochiR

/**
 * The contact editor, in create mode when the route names no contact and edit
 * mode when it does.
 */
@Composable
fun ContactEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ContactEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val form = uiState.form

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onDeleted()
    }

    if (viewModel.creating) {
        CreateEntityScaffold(
            title = stringResource(R.string.people_contact_new_title),
            submitLabel = stringResource(R.string.people_common_save),
            submitEnabled = form.valid && !uiState.isSaving,
            isBusy = uiState.isSaving,
            error = uiState.error,
            onBack = onBack,
            onSubmit = { viewModel.save() },
        ) { padding ->
            CreateEntityForm(padding) {
                ContactFields(
                    form = form,
                    books = uiState.books,
                    onChange = viewModel::updateForm,
                )
            }
        }
    } else {
        EditScaffold(
            state = uiState,
            onBack = onBack,
            onSave = { viewModel.save() },
            onRequestDelete = { viewModel.requestDelete() },
            onChange = viewModel::updateForm,
        )
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

@Composable
private fun EditScaffold(
    state: ContactEditUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit,
    onChange: (ContactForm) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    MochiScaffold(
        title = stringResource(R.string.people_contact_edit_title),
        onBack = onBack,
        actions = {
            MochiIconButton(
                onClick = onSave,
                enabled = state.form.valid && !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.people_common_save),
                    )
                }
            }
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
                        text = { Text(stringResource(R.string.people_contacts_delete)) },
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
                ContactFields(
                    form = state.form,
                    books = state.books,
                    onChange = onChange,
                )
            }
        }
    }
}

@Composable
private fun ContactFields(
    form: ContactForm,
    books: List<org.mochios.people.model.Book>,
    onChange: (ContactForm) -> Unit,
) {
    val types = CONTACT_TYPES.map { type -> type to stringResource(typeLabel(type)) }

    Section(title = stringResource(R.string.people_contact_name)) {
        LabelledField(stringResource(R.string.people_contact_name)) {
            CompactTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabelledField(stringResource(R.string.people_contact_given)) {
            CompactTextField(
                value = form.given,
                onValueChange = { onChange(form.copy(given = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabelledField(stringResource(R.string.people_contact_family)) {
            CompactTextField(
                value = form.family,
                onValueChange = { onChange(form.copy(family = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabelledField(stringResource(R.string.people_contact_nickname)) {
            CompactTextField(
                value = form.nickname,
                onValueChange = { onChange(form.copy(nickname = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Section(title = stringResource(R.string.people_contact_email)) {
        form.emails.forEachIndexed { index, entry ->
            TypedEntryRow(
                entry = entry,
                types = types,
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
        MochiTextButton(
            onClick = { onChange(form.copy(emails = form.emails + TypedEntry(type = TYPE_HOME))) },
        ) {
            Text(stringResource(R.string.people_contact_add_email))
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Section(title = stringResource(R.string.people_contact_phone)) {
        form.phones.forEachIndexed { index, entry ->
            TypedEntryRow(
                entry = entry,
                types = types,
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
        MochiTextButton(
            onClick = { onChange(form.copy(phones = form.phones + TypedEntry(type = TYPE_MOBILE))) },
        ) {
            Text(stringResource(R.string.people_contact_add_phone))
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Section(title = stringResource(R.string.people_contact_section_address)) {
        form.addresses.forEachIndexed { index, address ->
            AddressRows(
                address = address,
                types = types,
                onChange = { updated ->
                    onChange(form.copy(addresses = form.addresses.replaced(index, updated)))
                },
                onRemove = {
                    onChange(form.copy(addresses = form.addresses.without(index)))
                },
            )
        }
        MochiTextButton(
            onClick = {
                onChange(form.copy(addresses = form.addresses + AddressEntry(type = TYPE_HOME)))
            },
        ) {
            Text(stringResource(R.string.people_contact_add_address))
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Section(title = stringResource(R.string.people_contact_section_detail)) {
        LabelledField(stringResource(R.string.people_contact_birthday)) {
            BirthdayField(
                value = form.birthday,
                onValueChange = { onChange(form.copy(birthday = it)) },
            )
        }
        LabelledField(stringResource(R.string.people_contact_organisation)) {
            CompactTextField(
                value = form.organisation,
                onValueChange = { onChange(form.copy(organisation = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabelledField(stringResource(R.string.people_contact_title)) {
            CompactTextField(
                value = form.title,
                onValueChange = { onChange(form.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabelledField(stringResource(R.string.people_contact_url)) {
            CompactTextField(
                value = form.url.value,
                onValueChange = { onChange(form.copy(url = form.url.copy(value = it))) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabelledField(stringResource(R.string.people_contact_note)) {
            MochiTextField(
                value = form.note,
                onValueChange = { onChange(form.copy(note = it)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (books.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LabeledSelectField(
                label = stringResource(R.string.people_contact_book),
                placeholder = stringResource(R.string.people_contact_book),
                options = books.map { book -> book.id to book.name },
                selected = form.book,
                onSelect = { onChange(form.copy(book = it)) },
            )
        }
    }
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
    entry: TypedEntry,
    types: List<Pair<String, String>>,
    keyboardType: KeyboardType,
    placeholder: String,
    onChange: (TypedEntry) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        CompactTextField(
            value = entry.value,
            onValueChange = { onChange(entry.copy(value = it)) },
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                LabeledSelectField(
                    label = stringResource(R.string.people_contact_type),
                    placeholder = stringResource(R.string.people_contact_type),
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

@Composable
private fun AddressRows(
    address: AddressEntry,
    types: List<Pair<String, String>>,
    onChange: (AddressEntry) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        CompactTextField(
            value = address.street,
            onValueChange = { onChange(address.copy(street = it)) },
            placeholder = stringResource(R.string.people_contact_street),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        CompactTextField(
            value = address.city,
            onValueChange = { onChange(address.copy(city = it)) },
            placeholder = stringResource(R.string.people_contact_city),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        CompactTextField(
            value = address.region,
            onValueChange = { onChange(address.copy(region = it)) },
            placeholder = stringResource(R.string.people_contact_region),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        CompactTextField(
            value = address.postcode,
            onValueChange = { onChange(address.copy(postcode = it)) },
            placeholder = stringResource(R.string.people_contact_postcode),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        CompactTextField(
            value = address.country,
            onValueChange = { onChange(address.copy(country = it)) },
            placeholder = stringResource(R.string.people_contact_country),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                LabeledSelectField(
                    label = stringResource(R.string.people_contact_type),
                    placeholder = stringResource(R.string.people_contact_type),
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
    }
}

/**
 * `BDAY` as an ISO date behind the platform date picker, which is given a
 * locale that produces the user's own first day of the week — Material's
 * picker takes no first-day parameter of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayField(value: String, onValueChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val format = LocalFormat.current
    val seconds = birthdaySeconds(value)
    val display = when {
        value.isBlank() -> ""
        seconds != null -> format.formatDate(seconds)
        else -> value
    }

    // A read-only text field swallows taps, so an overlay on top makes the
    // whole box (not just the icon) open the picker.
    Box(modifier = Modifier.fillMaxWidth()) {
        MochiTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = stringResource(R.string.people_contact_pick_date),
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showPicker = true }
        )
    }

    if (showPicker) {
        val weekStartsOn = format.preferences.weekStartsOn
        val baseConfiguration = LocalConfiguration.current
        val localised = remember(baseConfiguration, weekStartsOn) {
            Configuration(baseConfiguration).apply { setLocale(localeForWeekStart(weekStartsOn)) }
        }
        CompositionLocalProvider(LocalConfiguration provides localised) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = seconds?.times(1000),
            )
            DatePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = {
                    MochiTextButton(onClick = {
                        val selected = pickerState.selectedDateMillis
                        if (selected != null) {
                            onValueChange(
                                java.time.LocalDate.ofEpochDay(selected / 86_400_000L).toString()
                            )
                        }
                        showPicker = false
                    }) {
                        Text(stringResource(R.string.people_contact_pick_date_confirm))
                    }
                },
                dismissButton = {
                    MochiTextButton(onClick = { showPicker = false }) {
                        Text(stringResource(R.string.people_common_cancel))
                    }
                },
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
}

/** Epoch seconds of an ISO `yyyy-MM-dd` birthday, null when it doesn't parse. */
private fun birthdaySeconds(value: String): Long? = try {
    java.time.LocalDate.parse(value).toEpochDay() * 86_400L
} catch (_: Exception) {
    null
}

/**
 * Map weekStartsOn (0=Sun … 6=Sat) to a representative Locale that gives the
 * DatePicker the right firstDayOfWeek. Beyond Sun/Mon/Sat there's no widely
 * used locale with the required day, so we fall back to the device default.
 */
private fun localeForWeekStart(weekStartsOn: Int): java.util.Locale = when (weekStartsOn) {
    0 -> java.util.Locale.US               // Sunday
    1 -> java.util.Locale("en", "GB")      // Monday
    6 -> java.util.Locale("ar", "SA")      // Saturday
    else -> java.util.Locale.getDefault()
}

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
