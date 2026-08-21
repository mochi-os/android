// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/** Narrowest a menu may be, so short labels still read as a panel rather than a chip. */
private val MenuMinWidth = 200.dp

/** Roomier than [MenuDefaults.DropdownMenuItemContentPadding], which leaves rows cramped. */
private val ItemPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)


/**
 * A [DropdownMenu] wearing what every floating surface in the app wears — see
 * [mochiPopupContainerColor], [mochiPopupShape] and [MochiPopupElevation].
 *
 * Two values here depart from `MenuTokens` on purpose, so leave them be rather than
 * restoring the Material defaults:
 *
 *  - **Container** `surfaceContainerHigh`, a step above the spec's `SurfaceContainer`.
 *    A menu opens over whatever is behind it, and cards now hold `surfaceContainer`
 *    themselves, so the step keeps a menu from landing on its own background.
 *  - **Row padding** 16dp against the spec's 12dp, see [ItemPadding]. The vertical 6dp
 *    sits inside Material's 48dp minimum row height, so a single-line row is unaffected.
 *
 * The shadow is the spec's own. It used to be 0, on the reasoning that the app separates
 * surfaces by tone alone — but tone alone cannot say which of two surfaces is in front,
 * and a menu that opened over a chat read as a patch of the page rather than something
 * on top of it.
 *
 * @param expanded Whether the menu is showing.
 * @param onDismissRequest Called when the user taps away or presses back.
 * @param modifier Applied to the menu container.
 * @param offset Displacement from the anchor's default position.
 * @param scrollState Scroll state for menus taller than the screen.
 * @param properties Popup behaviour, chiefly focusability.
 * @param content The menu rows, normally [MochiDropdownMenuItem].
 */
@Composable
fun MochiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = MenuMinWidth),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = mochiPopupShape(),
        containerColor = mochiPopupContainerColor(),
        shadowElevation = MochiPopupElevation,
        content = content
    )
}

/**
 * A row inside [MochiDropdownMenu]. Takes the same slots as Material's
 * `DropdownMenuItem` and adds the shared padding plus the app's two recoloured
 * states, so a row styled here cannot drift from every other menu in the app.
 *
 * Mark the chosen row of a picker with [selected] and a delete with [destructive]
 * rather than colouring by hand. [selected] also supplies the trailing check, so the
 * colour can never disagree with the mark — colour alone would not carry the state
 * for a colour-blind user, and the two drifting apart is exactly the bug this
 * parameter exists to prevent. Reach for [colors] only for a row neither flag covers.
 *
 * @param text The row's label content.
 * @param onClick Called when the row is tapped.
 * @param modifier Applied to the row.
 * @param leadingIcon Content before the label; rows read as a flat list without one,
 *   so prefer passing it.
 * @param trailingIcon Content on the trailing edge. Leave null on a [selected] row to
 *   get the standard check; pass a value only to show something else there.
 * @param enabled Whether the row responds to taps.
 * @param selected Whether this row is the chosen one in a picker. Tints the label and
 *   icons with the primary colour and adds the trailing check.
 * @param destructive Whether this row deletes something. Tints the label and icons
 *   with the error colour. Ignored when [selected] is true; no row is both.
 * @param colors Overrides the colours [selected] and [destructive] would apply. Null
 *   derives them from those flags.
 */
@Composable
fun MochiDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
    colors: MenuItemColors? = null
) {
    val accent = when {
        selected -> MaterialTheme.colorScheme.primary
        destructive -> MaterialTheme.colorScheme.error
        else -> null
    }
    val resolvedColors = colors ?: if (accent == null) {
        MenuDefaults.itemColors()
    } else {
        MenuDefaults.itemColors(
            textColor = accent,
            leadingIconColor = accent,
            trailingIconColor = accent
        )
    }
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon ?: if (selected) SelectedCheck else null,
        enabled = enabled,
        colors = resolvedColors,
        contentPadding = ItemPadding
    )
}

/** The mark on a [MochiDropdownMenuItem] the user has chosen. */
private val SelectedCheck: @Composable () -> Unit = {
    Icon(Icons.Outlined.Check, contentDescription = null)
}

/**
 * A hairline between groups of related rows. Menus long enough to need scanning read
 * better in groups; a menu of five or fewer rows rarely needs one.
 */
@Composable
fun MochiDropdownMenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(ItemPadding))
}

