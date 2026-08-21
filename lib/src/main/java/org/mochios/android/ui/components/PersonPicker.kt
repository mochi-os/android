// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.model.User
import org.mochios.android.util.SEARCH_DEBOUNCE

/**
 * A single-select person field, shown as a bordered box that reads like the
 * other outlined fields.
 *
 * While nothing is chosen the box shows a "Select …" placeholder; tapping it
 * opens a popup anchored to the box's bottom edge with a search field over a
 * list. When a person is assigned the list starts with a "None" row to clear
 * it, and the assigned person is shown ticked. Typing filters the project
 * [members] under a "Project members" heading and folds in directory matches
 * from [onSearch] under a "Directory" heading; when nothing matches it shows
 * "No people". Choosing a person closes the popup and shows their name in the
 * box with an "×" to clear it.
 *
 * The popup is placed by [FieldPopupPositionProvider], flush against the box's
 * bottom and growing upward, so it stays clear of the keyboard the search field
 * raises.
 *
 * @param selectedId   the currently stored subject id ([User.fingerprint]); blank means unset.
 * @param selectedName resolved display name for [selectedId], or null when it cannot be resolved.
 * @param members      project members, offered under "Project members".
 * @param onSelect     invoked with the chosen user.
 * @param onClear      invoked when "None" or the "×" clears the current selection.
 * @param onSearch     suspend directory search, debounced and run once the query has 2+ chars.
 */
@Composable
fun PersonPicker(
    selectedId: String?,
    selectedName: String?,
    members: List<User>,
    onSelect: (User) -> Unit,
    onClear: () -> Unit,
    onSearch: suspend (String) -> List<User>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf<List<User>>(emptyList()) }
    var lastSelected by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    var anchorBottom by remember { mutableIntStateOf(0) }
    // The popup hangs below the field until the search field is focused (which
    // raises the keyboard); then it flips above the field's bottom to clear it.
    // Driving off focus — not a live keyboard read — means one deliberate flip
    // on tap instead of a below→above blink on every open.
    var searchFocused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val popupPositionProvider = remember(searchFocused) {
        FieldPopupPositionProvider(anchorAbove = searchFocused)
    }
    // Bound the popup to the space above the field when flipped up, otherwise to
    // the space below it; leave a 16dp margin off the far edge.
    val windowHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val maxPopupHeight = with(density) {
        val availablePx = if (searchFocused) anchorBottom else windowHeightPx - anchorBottom
        (availablePx.coerceAtLeast(0).toDp() - 16.dp).coerceAtLeast(0.dp)
    }

    val hasSelection = !selectedId.isNullOrBlank()
    // Prefer the just-picked user's name so the box updates immediately, before
    // the parent's resolver catches up; fall back to the resolved name, then id.
    val displayName = when {
        !hasSelection -> null
        lastSelected != null && lastSelected?.fingerprint == selectedId -> lastSelected?.name
        !selectedName.isNullOrBlank() -> selectedName
        else -> selectedId
    }

    val memberMatches = if (query.isBlank()) {
        members
    } else {
        members.filter { member -> member.name.contains(query, ignoreCase = true) }
    }
    // Drop directory hits that are already listed as project members so nobody
    // appears twice.
    val directoryMatches = directory.filter { entry ->
        memberMatches.none { member -> member.fingerprint == entry.fingerprint }
    }
    // Only label the two groups when both are present; a single group needs no
    // heading.
    val showHeaders = memberMatches.isNotEmpty() && directoryMatches.isNotEmpty()

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable {
                    query = ""
                    directory = emptyList()
                    expanded = true
                }
                .onGloballyPositioned { coordinates ->
                    anchorWidth = coordinates.size.width
                    anchorBottom = coordinates.positionInWindow().y.toInt() +
                        coordinates.size.height
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasSelection) {
                EntityAvatar(
                    name = displayName.orEmpty(),
                    src = personAvatarPath(selectedId),
                    seed = selectedId,
                    size = 28.dp
                )
                Spacer(modifier = Modifier.size(12.dp))
            }
            Text(
                text = displayName ?: stringResource(R.string.person_picker_select),
                style = MaterialTheme.typography.bodyLarge,
                color = if (displayName != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (hasSelection) {
                MochiIconButton(
                    onClick = {
                        lastSelected = null
                        onClear()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.person_picker_clear),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (expanded) {
            Popup(
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = mochiPopupShape(),
                    color = mochiPopupContainerColor(),
                    shadowElevation = MochiPopupElevation,
                    modifier = Modifier
                        .width(with(density) { anchorWidth.toDp() })
                        .heightIn(max = maxPopupHeight)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        MochiTextField(
                            value = query,
                            onValueChange = { newQuery ->
                                query = newQuery
                                searchJob?.cancel()
                                if (newQuery.length >= 2) {
                                    searchJob = scope.launch {
                                        delay(SEARCH_DEBOUNCE)
                                        directory = onSearch(newQuery)
                                    }
                                } else {
                                    directory = emptyList()
                                }
                            },
                            placeholder = { Text(stringResource(R.string.person_picker_search)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .onFocusChanged { state -> searchFocused = state.isFocused }
                        )

                        Spacer(modifier = Modifier.size(8.dp))
                        HorizontalDivider()

                        val isEmpty = memberMatches.isEmpty() &&
                            directoryMatches.isEmpty() && !hasSelection
                        if (isEmpty) {
                            Text(
                                text = stringResource(R.string.person_picker_no_people),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                                // "None" clears the current pick; hidden while
                                // searching so it doesn't sit among results.
                                if (hasSelection && query.isBlank()) {
                                    item(key = "none") {
                                        NoneRow(onClick = {
                                            lastSelected = null
                                            onClear()
                                            expanded = false
                                        })
                                    }
                                }
                                if (memberMatches.isNotEmpty()) {
                                    if (showHeaders) {
                                        item(key = "members-header") {
                                            SectionHeader(
                                                stringResource(R.string.person_picker_project_members)
                                            )
                                        }
                                    }
                                    items(memberMatches, key = { user -> "m-${user.fingerprint}" }) { user ->
                                        PersonRow(
                                            user = user,
                                            selected = user.fingerprint == selectedId,
                                            onClick = {
                                                lastSelected = user
                                                onSelect(user)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                                if (directoryMatches.isNotEmpty()) {
                                    if (showHeaders) {
                                        item(key = "directory-header") {
                                            SectionHeader(
                                                stringResource(R.string.person_picker_directory)
                                            )
                                        }
                                    }
                                    items(directoryMatches, key = { user -> "d-${user.fingerprint}" }) { user ->
                                        PersonRow(
                                            user = user,
                                            selected = user.fingerprint == selectedId,
                                            onClick = {
                                                lastSelected = user
                                                onSelect(user)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small grey section heading inside the picker popup. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

/** The "None" row that clears the current assignment. */
@Composable
private fun NoneRow(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.person_picker_none),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 52.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
    )
}

/**
 * A tappable person row with an avatar and name. The currently-assigned person
 * is drawn with a leading tick and a highlighted background.
 */
@Composable
private fun PersonRow(
    user: User,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(8.dp))
        EntityAvatar(
            name = user.name,
            src = personAvatarPath(user.fingerprint),
            seed = user.fingerprint,
            size = 32.dp
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Places the picker popup at the anchor's left edge. It hangs below the field
 * ([anchorAbove] false); once the search field is focused ([anchorAbove] true)
 * it flips to grow upward with its bottom flush to the field's bottom, clearing
 * the keyboard the focus raises.
 */
private class FieldPopupPositionProvider(
    private val anchorAbove: Boolean
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val y = if (anchorAbove) {
            (anchorBounds.bottom - popupContentSize.height).coerceAtLeast(0)
        } else {
            anchorBounds.bottom
        }
        return IntOffset(anchorBounds.left, y)
    }
}
